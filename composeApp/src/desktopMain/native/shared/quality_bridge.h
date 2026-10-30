// Included by each host bridge after qualityActiveMpv and Q_* aliases are defined.
#include <clocale>
#include <string>
#include <vector>

static std::string qualityUtf8(JNIEnv *env, jstring text) {
    if (!text) return "";
    const char *chars = env->GetStringUTFChars(text, nullptr);
    std::string result(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(text, chars);
    return result;
}
#define QJNI(name) Java_com_nuvio_app_features_player_desktop_NativePlayerBridge_##name
extern "C" JNIEXPORT jstring JNICALL QJNI(qualityProperty)(JNIEnv *env, jobject, jlong handle, jboolean probe, jstring name) {
    auto mpv = qualityActiveMpv(handle);
    char *value = nullptr;
    std::string result;
    if (mpv && Q_GET(mpv, qualityUtf8(env, name).c_str(), MPV_FORMAT_STRING, &value) >= 0 && value) result = value;
    if (value) Q_FREE(value);
    return env->NewStringUTF(result.c_str());
}
extern "C" JNIEXPORT jboolean JNICALL QJNI(qualitySet)(JNIEnv *env, jobject, jlong handle, jboolean probe, jstring name, jstring value) {
    auto mpv = qualityActiveMpv(handle);
    return mpv && Q_SET(mpv, qualityUtf8(env, name).c_str(), qualityUtf8(env, value).c_str()) >= 0;
}
extern "C" JNIEXPORT jboolean JNICALL QJNI(qualityCommand)(JNIEnv *env, jobject, jlong handle, jboolean probe, jobjectArray args) {
    auto mpv = qualityActiveMpv(handle);
    if (!mpv || !args) return false;
    std::vector<std::string> strings;
    for (jsize i = 0; i < env->GetArrayLength(args); i++) {
        auto arg = (jstring)env->GetObjectArrayElement(args, i);
        strings.push_back(qualityUtf8(env, arg));
        env->DeleteLocalRef(arg);
    }
    std::vector<const char *> command;
    for (auto &s : strings) command.push_back(s.c_str());
    command.push_back(nullptr);
    return Q_COMMAND(mpv, command.data()) >= 0;
}
#undef QJNI
