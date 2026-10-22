#import <Cocoa/Cocoa.h>
#include <jni.h>
#include <atomic>
#include <memory>

namespace {
JavaVM *jvm = nullptr;
// Keep in sync with HistoryGesturePhase: Began, Ended, Cancelled, Unphased, Momentum.
enum Phase { Began = 0, Ended = 1, Cancelled = 2, Unphased = 3, Momentum = 4 };

struct Observer {
    jobject callback;
    jmethodID method;
    __strong id monitor;
    std::atomic<bool> active{true};

    ~Observer() {
        JNIEnv *env = nullptr;
        bool attached = jvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_EDETACHED;
        if (attached && jvm->AttachCurrentThread(reinterpret_cast<void **>(&env), nullptr) != JNI_OK) return;
        if (env) env->DeleteGlobalRef(callback);
        if (attached) jvm->DetachCurrentThread();
    }

    void emit(Phase phase) {
        if (!active.load()) return;
        JNIEnv *env = nullptr;
        bool attached = jvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_EDETACHED;
        if (attached && jvm->AttachCurrentThread(reinterpret_cast<void **>(&env), nullptr) != JNI_OK) return;
        if (env) {
            env->CallVoidMethod(callback, method, static_cast<jint>(phase));
            if (env->ExceptionCheck()) env->ExceptionClear();
        }
        if (attached) jvm->DetachCurrentThread();
    }
};
using GestureObserverHandle = std::shared_ptr<Observer>;

void onMain(dispatch_block_t block) {
    if ([NSThread isMainThread]) block();
    else dispatch_sync(dispatch_get_main_queue(), block);
}
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
    jvm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_nuvio_app_navigation_MacosHistoryGestureBridge_installNative(JNIEnv *env, jobject, jobject callback) {
    jclass type = env->GetObjectClass(callback);
    jmethodID method = env->GetMethodID(type, "onPhase", "(I)V");
    env->DeleteLocalRef(type);
    if (!method) return 0;
    auto observer = std::make_shared<Observer>();
    observer->callback = env->NewGlobalRef(callback);
    observer->method = method;
    onMain(^{
        observer->monitor = [NSEvent addLocalMonitorForEventsMatchingMask:NSEventMaskScrollWheel
            handler:^NSEvent *(NSEvent *event) {
                if (!observer->active.load() || !event.window || event.window != NSApp.keyWindow) return event;
                if (event.momentumPhase != NSEventPhaseNone) {
                    observer->emit(Momentum);
                } else if (event.phase & (NSEventPhaseBegan | NSEventPhaseMayBegin)) {
                    observer->emit(Began);
                } else if (event.phase & (NSEventPhaseEnded | NSEventPhaseCancelled)) {
                    const Phase phase = (event.phase & NSEventPhaseCancelled) ? Cancelled : Ended;
                    // Let AWT enqueue the final wheel delta first. The shared handle also
                    // keeps the callback alive if the window closes before this block runs.
                    dispatch_async(dispatch_get_main_queue(), ^{ observer->emit(phase); });
                } else if (event.phase == NSEventPhaseNone) {
                    observer->emit(Unphased);
                }
                return event;
            }];
    });
    if (!observer->monitor) return 0;
    return reinterpret_cast<jlong>(new GestureObserverHandle(observer));
}

extern "C" JNIEXPORT void JNICALL
Java_com_nuvio_app_navigation_MacosHistoryGestureBridge_uninstallNative(JNIEnv *, jobject, jlong token) {
    if (!token) return;
    auto handle = reinterpret_cast<GestureObserverHandle *>(token);
    auto observer = *handle;
    observer->active.store(false);
    onMain(^{
        if (observer->monitor) [NSEvent removeMonitor:observer->monitor];
        observer->monitor = nil;
    });
    delete handle;
}
