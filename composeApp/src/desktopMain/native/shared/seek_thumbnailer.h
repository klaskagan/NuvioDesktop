// Seek-bar thumbnails: a second, headless mpv instance per player that decodes
// single keyframes and writes them as small JPEGs.
//
// Included by each host bridge after quality_bridge.h, so the Q_* aliases
// (static libmpv on macOS/Linux, the dynamically loaded API on Windows) and
// qualityUtf8 are already defined. The bridge must also define Q_WAKEUP.
//
// Uses only mpv_create/initialize/command/get_property/wait_event/wakeup/
// terminate_destroy: no render context, no video output (vo=null) and no GPU,
// so it never competes with the playing instance's renderer.
#include <atomic>
#include <chrono>
#include <clocale>
#include <cmath>
#include <cstdio>
#include <mutex>
#include <string>
#include <vector>

namespace nuvio_seek_thumbs {

struct Thumbnailer {
    mpv_handle *mpv = nullptr;
    std::mutex lock;               // one grab at a time; destroy waits for it
    std::atomic<bool> closing{false};
    bool ready = false;            // first frame decoded after loadfile
    bool failed = false;           // file ended or could not be opened
};

static std::string escapeHeaderField(const std::string &line) {
    std::string out;
    out.reserve(line.size() + 4);
    for (char ch : line) {
        if (ch == '\\' || ch == ',') out.push_back('\\');
        out.push_back(ch);
    }
    return out;
}

static Thumbnailer *create(const std::string &url, const std::vector<std::string> &headerLines) {
    if (url.empty()) return nullptr;
    // A regional numeric locale makes mpv_create fail (see the player bridges).
    std::setlocale(LC_NUMERIC, "C");
    mpv_handle *mpv = Q_CREATE();
    if (!mpv) return nullptr;
    const char *options[][2] = {
        {"config", "no"},
        {"terminal", "no"},
        {"input-default-bindings", "no"},
        {"osc", "no"},
        {"load-scripts", "no"},
        {"ytdl", "no"},
        {"vo", "null"},
        {"ao", "null"},
        {"audio", "no"},
        {"sid", "no"},
        {"sub-auto", "no"},
        {"audio-file-auto", "no"},
        {"pause", "yes"},
        {"idle", "yes"},
        {"keep-open", "always"},
        // Keyframe seeks: one packet per thumbnail instead of decoding up to the target.
        {"hr-seek", "no"},
        {"hwdec", "no"},
        {"vd-lavc-threads", "2"},
        {"vf", "scale=w=320:h=-2"},
        // One keyframe per seek: no read-ahead. Over HTTP each seek costs about
        // one server round trip either way; this only avoids fetching extra data.
        {"cache", "no"},
        {"demuxer-readahead-secs", "0"},
        {"demuxer-max-bytes", "4MiB"},
        {"demuxer-max-back-bytes", "0"},
        {"screenshot-format", "jpg"},
        {"screenshot-jpeg-quality", "75"},
        {"screenshot-tag-colorspace", "no"},
        {"screenshot-high-bit-depth", "no"},
        {"stream-lavf-o",
         "reconnect=1,reconnect_streamed=1,reconnect_on_network_error=1,"
         "reconnect_on_http_error=%23%408,429,500,502,503,504,reconnect_delay_max=15"},
    };
    for (auto &option : options) Q_OPTION(mpv, option[0], option[1]);
    if (!headerLines.empty()) {
        std::string fields;
        for (const std::string &line : headerLines) {
            if (!fields.empty()) fields.push_back(',');
            fields += escapeHeaderField(line);
        }
        Q_OPTION(mpv, "http-header-fields", fields.c_str());
    }
    if (Q_INITIALIZE(mpv) < 0) {
        Q_DESTROY(mpv);
        return nullptr;
    }
    const char *load[] = {"loadfile", url.c_str(), nullptr};
    if (Q_COMMAND(mpv, load) < 0) {
        Q_DESTROY(mpv);
        return nullptr;
    }
    auto *thumbnailer = new Thumbnailer();
    thumbnailer->mpv = mpv;
    return thumbnailer;
}

// 1: frame ready, 0: timed out or closing, -1: the file ended or failed to open.
static int awaitPlaybackRestart(Thumbnailer *t, long long timeoutMs) {
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(timeoutMs);
    while (!t->closing.load()) {
        const auto left = std::chrono::duration<double>(deadline - std::chrono::steady_clock::now()).count();
        if (left <= 0) return 0;
        mpv_event *event = Q_WAIT(t->mpv, left < 0.1 ? left : 0.1);
        if (!event) continue;
        switch (event->event_id) {
            case MPV_EVENT_PLAYBACK_RESTART: return 1;
            case MPV_EVENT_END_FILE:
            case MPV_EVENT_SHUTDOWN: return -1;
            default: break;
        }
    }
    return 0;
}

// Drops events left over from an earlier grab that timed out, so a late restart
// is not mistaken for the one this seek produces.
static void drainEvents(Thumbnailer *t) {
    while (true) {
        mpv_event *event = Q_WAIT(t->mpv, 0);
        if (!event || event->event_id == MPV_EVENT_NONE) return;
        if (event->event_id == MPV_EVENT_END_FILE || event->event_id == MPV_EVENT_SHUTDOWN) t->failed = true;
    }
}

// Writes the keyframe at or before positionMs to outputPath. Returns the frame's
// position in ms (>= 0), -1 when the source cannot produce thumbnails, or -2 on
// a timeout or while closing (the caller may retry later).
static long long frame(Thumbnailer *t, long long positionMs, const std::string &outputPath, long long timeoutMs) {
    std::lock_guard<std::mutex> guard(t->lock);
    if (t->closing.load()) return -2;
    if (t->failed) return -1;
    if (!t->ready) {
        const int opened = awaitPlaybackRestart(t, timeoutMs);
        if (opened < 0) { t->failed = true; return -1; }
        if (opened == 0) return -2;
        t->ready = true;
    }
    drainEvents(t);
    if (t->failed) return -1;
    char seconds[48];
    std::snprintf(seconds, sizeof(seconds), "%.3f", (double)(positionMs < 0 ? 0 : positionMs) / 1000.0);
    const char *seek[] = {"seek", seconds, "absolute+keyframes", nullptr};
    if (Q_COMMAND(t->mpv, seek) < 0) return -1;
    const int restarted = awaitPlaybackRestart(t, timeoutMs);
    if (restarted < 0) { t->failed = true; return -1; }
    if (restarted == 0) return -2;
    const char *shot[] = {"screenshot-to-file", outputPath.c_str(), "video", nullptr};
    if (Q_COMMAND(t->mpv, shot) < 0) return -2;
    double timePos = 0;
    if (Q_GET(t->mpv, "time-pos", MPV_FORMAT_DOUBLE, &timePos) < 0 || !std::isfinite(timePos)) {
        timePos = (double)positionMs / 1000.0;
    }
    return timePos <= 0 ? 0 : (long long)std::llround(timePos * 1000.0);
}

static void destroy(Thumbnailer *t) {
    if (!t) return;
    t->closing.store(true);
    if (t->mpv) Q_WAKEUP(t->mpv);
    {
        // Waits for an in-flight grab; it observes `closing` within ~100 ms.
        std::lock_guard<std::mutex> guard(t->lock);
        if (t->mpv) Q_DESTROY(t->mpv);
        t->mpv = nullptr;
    }
    delete t;
}

}  // namespace nuvio_seek_thumbs

#define STJNI(name) Java_com_nuvio_app_features_player_desktop_NativePlayerBridge_##name
extern "C" JNIEXPORT jlong JNICALL STJNI(seekThumbnailerCreate)(JNIEnv *env, jobject, jstring url, jobjectArray headerLines) {
    std::vector<std::string> headers;
    if (headerLines) {
        for (jsize i = 0; i < env->GetArrayLength(headerLines); i++) {
            auto line = (jstring)env->GetObjectArrayElement(headerLines, i);
            headers.push_back(qualityUtf8(env, line));
            env->DeleteLocalRef(line);
        }
    }
    try {
        return (jlong)(intptr_t)nuvio_seek_thumbs::create(qualityUtf8(env, url), headers);
    } catch (...) {
        return 0;  // Windows: libmpv could not be loaded.
    }
}
extern "C" JNIEXPORT jlong JNICALL STJNI(seekThumbnailerFrame)(JNIEnv *env, jobject, jlong handle, jlong positionMs, jstring outputPath, jlong timeoutMs) {
    auto *t = (nuvio_seek_thumbs::Thumbnailer *)(intptr_t)handle;
    if (!t) return -1;
    return (jlong)nuvio_seek_thumbs::frame(t, (long long)positionMs, qualityUtf8(env, outputPath), (long long)timeoutMs);
}
extern "C" JNIEXPORT void JNICALL STJNI(seekThumbnailerDestroy)(JNIEnv *, jobject, jlong handle) {
    nuvio_seek_thumbs::destroy((nuvio_seek_thumbs::Thumbnailer *)(intptr_t)handle);
}
#undef STJNI
