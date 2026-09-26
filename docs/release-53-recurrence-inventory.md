# Complete version-53 Firebase recurrence inventory

Import 6; September 1–26, 2026. One version-filtered sample per group.
Exact-ID history is provider/app scoped. Previous event counts have different exposure
and windows and are not rates. `—` means absent from stored earlier versions, not
proof of a new defect. Revision `adbaffe` is the reported build, not this fix candidate.
See [analysis and fix attempts](release-53-recurrence.md).

| Issue | Type / events | Earlier versions | Sample build | Sample frame | Disposition |
| --- | --- | --- | --- | --- | --- |
| `4d05f9e74e77520b418eac3a355108f1` | anr / 204 | 52, 51, 49, 48 | `adbaffe` | `android.os.MessageQueue.nativePollOnce` | Idle native sample; request ANR reason/full variants |
| `15c1049c4d0bc536c073e5cbbeef1b7c` | anr / 46 | 52, 51, 49, 48 | `adbaffe` | `android.os.MessageQueue.nativePollOnce` | Idle native sample; request ANR reason/full variants |
| `ec94f9d136e7da26d520d47942d46deb` | anr / 21 | 52, 51, 49, 48 | `adbaffe` | `art::ConditionVariable::WaitHoldingLocks` | Runtime/framework/SDK sample; full timed trace needed |
| `293b2117cdf3ed5f2c38dd6de73a722a` | anr / 18 | 52, 51, 48 | `adbaffe` | `m7.es.m` | Measurement/licensing service wait; full service trace needed |
| `302d6c90af10653f908aac894932b278` | anr / 14 | 52, 51, 49, 48 | `adbaffe` | `__ioctl` | Runtime/framework/SDK sample; full timed trace needed |
| `7487bf9aa6a13353cfd84b898fa70a9d` | anr / 14 | 52, 51, 49, 48 | `adbaffe` | `android.os.MessageQueue.nativePollOnce` | Idle native sample; request ANR reason/full variants |
| `49e77967e24836ed20e1450247201422` | anr / 11 | 51, 49, 48 | `adbaffe` | `android::uirenderer::ThreadBase::waitForWork` | Render/driver wait; capture system trace before changes |
| `a5c1f855585a55e6a9cf91d97c82b5ea` | anr / 11 | 52, 51, 49, 48 | `adbaffe` | `art::ConditionVariable::WaitHoldingLocks` | Runtime/framework/SDK sample; full timed trace needed |
| `416f1c679b091716c78157591849051c` | anr / 9 | 52, 51, 49, 48 | `adbaffe` | `android::uirenderer::ThreadBase::waitForWork` | Render/driver wait; capture system trace before changes |
| `958ed166f1584b7c22378e82ad6968b1` | anr / 7 | 52, 51, 49, 48 | `adbaffe` | `art::ConditionVariable::WaitHoldingLocks` | Runtime/framework/SDK sample; full timed trace needed |
| `1ea5988c748d88947b0a46bb05ff104d` | anr / 5 | 52 | `adbaffe` | `com.jeerovan.comfer.WidgetHostManager$startListening$1.invokeSuspend` | Start Binder wait; 51-02 view-thread constraint retained; investigate |
| `28051541328b9d04813ec0dbc5ef264a` | anr / 5 | 51, 48 | `adbaffe` | `m7.es.m` | Measurement/licensing service wait; full service trace needed |
| `7be898be9b6f6bbe3d90f85674200043` | crash / 5 | 51, 48 | `adbaffe` | `android.app.ActivityClient.activityStopped` | System-server death; no app root cause established |
| `0759651f42c5b565e22114f551d539fa` | anr / 4 | 52, 51, 48 | `adbaffe` | `android.os.BinderProxy.transactNative` | Runtime/framework/SDK sample; full timed trace needed |
| `4e241f2d06c3a545132bfd9722932912` | anr / 4 | 51, 48 | `adbaffe` | `com.jeerovan.comfer.AppInfoViewModelKt$getAppInfo$2$1.invokeSuspend` | Background icon/resource lock; retain serialization, capture lock owner |
| `6ab80b425c770d5deb87bc74a144aa59` | anr / 4 | 51, 48 | `adbaffe` | `__futex_wait_ex` | Runtime/framework/SDK sample; full timed trace needed |
| `901023f312e4c8a37f604ccfb3ec41c6` | anr / 4 | 52, 51, 49, 48 | `adbaffe` | `com.jeerovan.comfer.MainActivityKt$LauncherTwoPaneLayout$1$1.measure-3p2s80s` | App/UI sample; profile timed critical path, cause unproven |
| `bd2ab4706a80a4e93f89effbf4ac6300` | crash / 4 | — | `adbaffe` | `c0.b.M` | 53-09 implemented and locally tested; production unverified |
| `1b9b9a1021c548bb3c8e6b665f963fe1` | anr / 3 | 51, 48 | `adbaffe` | `art::ConditionVariable::WaitHoldingLocks(art::Thread*)` | Runtime/framework/SDK sample; full timed trace needed |
| `348157e030bac748d3d1262e0edc1d81` | anr / 3 | 52, 51, 48 | `adbaffe` | `android::android_os_MessageQueue_nativePollOnce` | Idle native sample; request ANR reason/full variants |
| `36293788e50e498455ee6f557bd0a311` | crash / 3 | 51, 48 | `adbaffe` | `android.app.ActivityThread.handleUnbindService` | System-server death; no app root cause established |
| `47929b792926cd22326529bb4ccc5a6c` | anr / 3 | 51, 48 | `adbaffe` | `cmpbe_v2_compile_multiple_shaders` | Render/driver wait; capture system trace before changes |
| `8cea93e60ac6516390e783687ba4e15e` | anr / 3 | 52, 51, 49 | `adbaffe` | `com.pairip.licensecheck.LicenseClient.bindToLicensingService` | Measurement/licensing service wait; full service trace needed |
| `8d148077ccbe90466167a0aa6fc2be1d` | anr / 3 | 52, 51, 49, 48 | `adbaffe` | `glProgramBinary` | Render/driver wait; capture system trace before changes |
| `973a7a1429c335323bfe72f30dad1df7` | crash / 3 | 52, 51, 48 | `adbaffe` | `android.app.ActivityClient.activityResumed` | System-server death; no app root cause established |
| `de9f6297165cb12f529166bea25085e4` | anr / 3 | — | `adbaffe` | `com.jeerovan.comfer.WidgetHostManager$stopListening$1.invokeSuspend` | 53-08 implemented and locally tested; production unverified |
| `05babf260a655d1e7a2738eef61f0a2b` | anr / 2 | 51 | `adbaffe` | `com.jeerovan.comfer.ComferApp.setupImageWorker` | 53-07 implemented and locally tested; production unverified |
| `172df327e7958436eb8fa58bf8efb21a` | anr / 2 | 51, 48 | `adbaffe` | `m7.es.m` | Measurement/licensing service wait; full service trace needed |
| `172e5e87ec9f8a292d4f3de43cb987d2` | anr / 2 | — | `adbaffe` | `com.jeerovan.comfer.WidgetHostManager$stopListening$1.invokeSuspend` | 53-08 implemented and locally tested; production unverified |
| `3991552a7f31cef4bd941b81fbfd214e` | anr / 2 | — | `adbaffe` | `com.jeerovan.comfer.WidgetHostManager$stopListening$1.invokeSuspend` | 53-08 implemented and locally tested; production unverified |
| `4593bd5c8c65cb959be5aea7506dc75f` | anr / 2 | 52, 51, 48 | `adbaffe` | `android.os.MessageQueue.nativePollOnce` | Idle native sample; request ANR reason/full variants |
| `4eb164efb834896b9969a5069ebea51c` | anr / 2 | 51 | `adbaffe` | `__dl_do_dl_iterate_phdr` | Runtime/framework/SDK sample; full timed trace needed |
| `56b0e3cc79d65d007caef73adb3ccbaa` | anr / 2 | 48 | `adbaffe` | `android.os.MessageQueue.nativePollOnce` | Idle native sample; request ANR reason/full variants |
| `57cdeb82a64afd66721fcc7ed02b383e` | anr / 2 | 51, 48 | `adbaffe` | `art::DumpNativeStack` | Runtime/framework/SDK sample; full timed trace needed |
| `5fd290dc822b886597cb9573d07bfddd` | anr / 2 | — | `adbaffe` | `androidx.compose.ui.graphics.drawscope.DrawScope.getSize-NH-jbRc` | App/UI sample; profile timed critical path, cause unproven |
| `6bba884050263fdff281a8bc49a85b9b` | anr / 2 | 51 | `adbaffe` | `android.os.MessageQueue.nativePollOnce` | Idle native sample; request ANR reason/full variants |
| `7143923d65b86aafd879de983c99d964` | anr / 2 | 51, 49, 48 | `adbaffe` | `com.jeerovan.comfer.utils.CommonUtil.handleStartActivity` | App/UI sample; profile timed critical path, cause unproven |
| `83d4ab4722c15cc3aa1166087936712f` | anr / 2 | 49 | `adbaffe` | `kotlinx.coroutines.flow.internal.AbstractSharedFlow.freeSlot` | Runtime/framework/SDK sample; full timed trace needed |
| `967976fa3a49fe618dae41b85c7a49fb` | anr / 2 | 51, 48 | `adbaffe` | `android::uirenderer::ThreadBase::waitForWork` | Render/driver wait; capture system trace before changes |
| `96de289334e54a302779cb891d83b9bb` | anr / 2 | 51, 48 | `adbaffe` | `__ioctl` | Runtime/framework/SDK sample; full timed trace needed |
| `9daa8cd8f9283fb6bbe0cad4c42e4573` | anr / 2 | — | `adbaffe` | `androidx.compose.ui.semantics.SemanticsNode.getUnmergedConfig$ui` | App/UI sample; profile timed critical path, cause unproven |
| `c138b4711cc7b236dcb45dd51063eb68` | anr / 2 | — | `adbaffe` | `android.view.inputmethod.IInputMethodManagerGlobalInvoker.startInputOrWindowGainedFocusAsync` | Runtime/framework/SDK sample; full timed trace needed |
| `cb9593d762b12a0d8e0246b6e5108f84` | anr / 2 | 51, 48 | `adbaffe` | `android::uirenderer::ThreadBase::waitForWork` | Render/driver wait; capture system trace before changes |
| `ceb2a51d6d2b2bc5b77d105e229094a3` | anr / 2 | 51 | `adbaffe` | `m7.es.m` | Measurement/licensing service wait; full service trace needed |
| `d3bd7bf3f5d04282117bcbba1b067ba4` | anr / 2 | 51 | `adbaffe` | `__dl_do_dl_iterate_phdr` | Runtime/framework/SDK sample; full timed trace needed |
| `d407e59611040a5624a808c1c021b4e8` | anr / 2 | 51, 49, 48 | `adbaffe` | `cmpbe_v2_compile_multiple_shaders` | Render/driver wait; capture system trace before changes |
| `e6d32406f6b5f77622b8b3c023c516b1` | anr / 2 | — | `adbaffe` | `com.jeerovan.comfer.MainActivityKt.AnimatedBackground` | App/UI sample; profile timed critical path, cause unproven |
| `f76d00024b6fabfbea2c0368a25ea3af` | anr / 2 | 51, 48 | `adbaffe` | `com.jeerovan.comfer.MainActivityKt$LauncherTwoPaneLayout$1$1.measure-3p2s80s` | App/UI sample; profile timed critical path, cause unproven |
| `000cd59fcb2bded45a83b775bc25b93d` | anr / 1 | — | `adbaffe` | `android.view.InputEventReceiver.nativeFinishInputEvent` | Runtime/framework/SDK sample; full timed trace needed |
| `01324a801910cbebbafd79b2317b0fd7` | anr / 1 | — | `adbaffe` | `androidx.compose.ui.unit.fontscaling.FontScaleConverterFactory.forScale` | App/UI sample; profile timed critical path, cause unproven |
| `01b2470c281193e6f14643cc765f4a1e` | anr / 1 | 49, 48 | `adbaffe` | `art::DumpNativeStack` | Runtime/framework/SDK sample; full timed trace needed |
| `01b3c3eb87216c32eb32563bf5b3bb99` | anr / 1 | — | `adbaffe` | `com.jeerovan.comfer.UShapeScrollStateKt$uShapeScrollGestures$3$1$1.invokeSuspend` | App/UI sample; profile timed critical path, cause unproven |
| `04cac75e50e3aaef706628bc4f487076` | anr / 1 | — | `adbaffe` | `androidx.compose.ui.graphics.AndroidCanvas.translate` | App/UI sample; profile timed critical path, cause unproven |
| `076df538635ca747261434c866445f39` | anr / 1 | — | `adbaffe` | `android.graphics.Matrix.reset` | Runtime/framework/SDK sample; full timed trace needed |
| `084a4555bd6d66067c80e16a01faee55` | anr / 1 | — | `adbaffe` | `androidx.compose.ui.input.pointer.NodeParent.buildCache` | App/UI sample; profile timed critical path, cause unproven |
| `0c11f84214db742710492236555e0893` | anr / 1 | — | `adbaffe` | `android.view.ViewRootInsetsControllerHost.releaseSurfaceControlFromRt` | Runtime/framework/SDK sample; full timed trace needed |
| `0f910872c6d4c69cf652ffbdbc96bd35` | anr / 1 | — | `adbaffe` | `android.os.OneTraceExtImpl.isOneTraceEnable` | Runtime/framework/SDK sample; full timed trace needed |
| `104ef223694a40d93a0e62e7bd1cadf9` | anr / 1 | — | `adbaffe` | `androidx.compose.foundation.gestures.TapGestureDetectorKt$detectTapAndPress$2$1$2.u` | App/UI sample; profile timed critical path, cause unproven |
| `111b9a85531a49e5b8e5e5b017dfa83b` | anr / 1 | — | `adbaffe` | `!!!0000!59bef17764eba5e897c3f9ebcb5264!a22bdbeed1!` | Runtime/framework/SDK sample; full timed trace needed |
| `12accf87f5aa9692ce9e521e5dd1a753` | anr / 1 | 48 | `adbaffe` | `com.jeerovan.comfer.utils.CommonUtil.handleStartActivity` | App/UI sample; profile timed critical path, cause unproven |
| `138b0c2b4bc0f6c06c56ee8c7292b06a` | anr / 1 | — | `adbaffe` | `androidx.lifecycle.ProcessLifecycleOwner.attach$lifecycle_process_release` | Runtime/framework/SDK sample; full timed trace needed |
| `14f4799f1253fd49165c1e3da48f6a4c` | anr / 1 | 48 | `adbaffe` | `com.jeerovan.comfer.MainActivityKt.LongPressHint` | App/UI sample; profile timed critical path, cause unproven |
| `1b1c16c2b655108b91d97f1c40168fa2` | anr / 1 | 52 | `adbaffe` | `androidx.compose.ui.Modifier$Node.getAggregateChildKindSet$ui` | App/UI sample; profile timed critical path, cause unproven |
| `1bb48fa985f3160d6aef914f924a5760` | anr / 1 | — | `adbaffe` | `com.jeerovan.comfer.WidgetHostManager$stopListening$1.invokeSuspend` | 53-08 implemented and locally tested; production unverified |
| `1bc7322e5a6abae5ef4a9d477e3a17bc` | anr / 1 | — | `adbaffe` | `androidx.compose.runtime.snapshots.Snapshot$Companion.getCurrent` | App/UI sample; profile timed critical path, cause unproven |
| `22039901473fbf64ab97fff84806a2a2` | anr / 1 | — | `adbaffe` | `com.jeerovan.comfer.MainActivityKt.QuickListOverlay$lambda$60$0$2$4$0` | App/UI sample; profile timed critical path, cause unproven |
| `269dcd2148e4583abc4226ddd7d39460` | anr / 1 | — | `adbaffe` | `com.jeerovan.comfer.MainActivity.<init>` | App/UI sample; profile timed critical path, cause unproven |
| `2b84bdda48771d8c2554e7d66598db2c` | anr / 1 | — | `adbaffe` | `androidx.compose.ui.platform.AndroidComposeView.isBadMotionEvent` | App/UI sample; profile timed critical path, cause unproven |
| `2bdaa90e21b71caad2450bff3f478830` | anr / 1 | 51, 48 | `adbaffe` | `com.jeerovan.comfer.utils.CommonUtil.handleStartActivity` | App/UI sample; profile timed critical path, cause unproven |
| `2e4405c2a4593862074cc6086010f9f8` | anr / 1 | — | `adbaffe` | `com.jeerovan.comfer.SettingsKt$$ExternalSyntheticLambda73.j` | App/UI sample; profile timed critical path, cause unproven |
| `30527f0ec3480b2de09b7baa40bdff50` | anr / 1 | — | `adbaffe` | `android.os.BinderProxy.transactNative` | Runtime/framework/SDK sample; full timed trace needed |
| `30805a6146c0b33feed724acdf88553d` | crash / 1 | 51 | `adbaffe` | `android.app.ActivityThread.handleCreateService` | System-server death; no app root cause established |
| `31ab52dbcd7ebd34bf089dcf81e3cd00` | anr / 1 | — | `adbaffe` | `hihonor.hiview.HiDiagnostic.report` | Runtime/framework/SDK sample; full timed trace needed |
| `33e3452aab3a8d6b4df35a746baea611` | anr / 1 | — | `adbaffe` | `com.jeerovan.comfer.MainActivityKt$WidgetInstance$2$1$5$1.invokeSuspend` | App/UI sample; profile timed critical path, cause unproven |
| `3421a06d2a7849a23d684c5132cf01fc` | crash / 1 | — | `adbaffe` | `android.os.Parcel.readTypedArray` | Framework window/input failure; reproduce affected OS before mitigation |
| `34d855c789cc886012f9872ebe79b41d` | anr / 1 | — | `adbaffe` | `androidx.compose.animation.AnimatedContentKt$AnimatedContentImpl$8$1$3$1.n` | App/UI sample; profile timed critical path, cause unproven |
| `34e767565de7816f82a84728800c6048` | anr / 1 | — | `adbaffe` | `com.jeerovan.comfer.MainActivityKt$WidgetInstance$2$1$5$1.invokeSuspend` | App/UI sample; profile timed critical path, cause unproven |
| `363d4e2ee15ac558d0ccc57aaf150224` | anr / 1 | — | `adbaffe` | `androidx.compose.ui.layout.Placeable$PlacementScope.l` | App/UI sample; profile timed critical path, cause unproven |
| `3658b40189e053949035f05f725177a9` | anr / 1 | — | `adbaffe` | `android.os.BinderProxy.transactNative` | Runtime/framework/SDK sample; full timed trace needed |
| `373c5480a83fbdb04c8ab7f585c16078` | anr / 1 | — | `adbaffe` | `java.lang.StringBuilder.<init>` | Runtime/framework/SDK sample; full timed trace needed |
| `397935881d3ba78208423c74cb4ad3a0` | anr / 1 | — | `adbaffe` | `android.os.ufw.UltraFrameworkComponentFactoryImpl.makeUnisocChoreographer` | Runtime/framework/SDK sample; full timed trace needed |
| `3a9e98b205a697e0e17b325c5f28c195` | anr / 1 | — | `adbaffe` | `androidx.collection.ObjectIntMapKt.<clinit>` | Runtime/framework/SDK sample; full timed trace needed |
| `3e24ea7b12bc8def045f06efa8d887a9` | anr / 1 | — | `adbaffe` | `com.transsion.hubcore.utils.TranClassInfo.getImpl` | Runtime/framework/SDK sample; full timed trace needed |
| `463950bf51de153c38887c10c7d849c3` | anr / 1 | 48 | `adbaffe` | `com.google.firebase.tracing.ComponentMonitor.lambda$processRegistrar$0` | Runtime/framework/SDK sample; full timed trace needed |
| `46c23601d510c028fa30a5d1348e7d83` | anr / 1 | — | `adbaffe` | `vivo.accessibilityenhance.TouchMotionHandler.startAccessibilityEnhanceDouble` | Runtime/framework/SDK sample; full timed trace needed |
| `48439ee3545ed1f60c6d7dba30e12771` | anr / 1 | — | `adbaffe` | `android::android_os_MessageQueue_nativePollOnce(_JNIEnv*, _jobject*, long, int)` | Idle native sample; request ANR reason/full variants |
| `4d0b166777d01fd55d94625f117262e0` | anr / 1 | 51 | `adbaffe` | `android::android_os_MessageQueue_nativePollOnce(_JNIEnv*, _jobject*, long long, int)` | Idle native sample; request ANR reason/full variants |
| `4e84b1170532150189dff4b212ab1ce9` | anr / 1 | 51 | `adbaffe` | `__ioctl` | Runtime/framework/SDK sample; full timed trace needed |
| `4f090cab99e7f2d786569f757485f471` | anr / 1 | — | `adbaffe` | `androidx.compose.ui.focus.FocusTargetNode.onDetach` | App/UI sample; profile timed critical path, cause unproven |
| `51ab3f8f0c820211f368dbd7527432fc` | anr / 1 | — | `adbaffe` | `android.os.MessageQueue.nativePollOnce` | Idle native sample; request ANR reason/full variants |
| `54a0f5c072b589dd672365aeacd5d9d3` | anr / 1 | — | `adbaffe` | `com.google.firebase.sessions.ProcessDetailsProvider.getAppProcessDetails` | Runtime/framework/SDK sample; full timed trace needed |
| `55d82a0291f683daf3e041fa14265d38` | anr / 1 | — | `adbaffe` | `com.google.firebase.sessions.ProcessDetailsProvider.getAppProcessDetails` | Runtime/framework/SDK sample; full timed trace needed |
| `5686cb9b4b98bbe0481398f78fcd0b79` | anr / 1 | — | `adbaffe` | `android.app.QueuedWork.processPendingWork` | Runtime/framework/SDK sample; full timed trace needed |
| `56a26814aa415e62c083bd719c01a9e3` | anr / 1 | — | `adbaffe` | `androidx.appcompat.view.WindowCallbackWrapper.dispatchTouchEvent` | Runtime/framework/SDK sample; full timed trace needed |
| `579db177a4edaca74673c29246c62eb9` | anr / 1 | — | `adbaffe` | `miui.util.TypefaceHelper.createTypeface` | Runtime/framework/SDK sample; full timed trace needed |
| `59591d9ffd7a0caeda5848b1278b7b33` | anr / 1 | — | `adbaffe` | `androidx.startup.InitializationProvider.<init>` | Runtime/framework/SDK sample; full timed trace needed |
| `5d5fc614b678ffa03a4eadb02f3b1a6a` | anr / 1 | — | `adbaffe` | `com.google.firebase.tracing.ComponentMonitor.lambda$processRegistrar$0` | Runtime/framework/SDK sample; full timed trace needed |
| `5de3cb9a182186a8ce345589e2930f47` | anr / 1 | — | `adbaffe` | `com.mediatek.boostfwk.utils.Util.isGameApp` | Runtime/framework/SDK sample; full timed trace needed |
| `5e9c7f3ad8a1677b39367e4b1c4680af` | anr / 1 | 48 | `adbaffe` | `com.jeerovan.comfer.MainActivity.onCreate` | App/UI sample; profile timed critical path, cause unproven |
| `5eb5a76430878b280c7a35747c0c80d7` | anr / 1 | — | `adbaffe` | `__ioctl` | Runtime/framework/SDK sample; full timed trace needed |
| `60422696a3739282633999ccb01fd2f2` | anr / 1 | — | `adbaffe` | `com.jeerovan.comfer.MainActivityKt.w` | App/UI sample; profile timed critical path, cause unproven |
| `644a7adc0fda1825e83223a6724ef595` | anr / 1 | 51 | `adbaffe` | `com.mediatek.boostfwk.identify.scroll.ScrollIdentify.inputEventCheck` | Runtime/framework/SDK sample; full timed trace needed |
| `65a9bf8cc690d48beacbeaed88a73c3f` | anr / 1 | — | `adbaffe` | `androidx.collection.MutableScatterMap.g` | Runtime/framework/SDK sample; full timed trace needed |
| `672ef83856bef36e4c45fd87afe4021f` | anr / 1 | 48 | `adbaffe` | `art::DumpNativeStack(std::__1::basic_ostream<char, std::__1::char_traits<char> >&, int, BacktraceMap*, char const*, art::ArtMethod*, void*, bool)` | Runtime/framework/SDK sample; full timed trace needed |
| `6a82cbfff65892f4aa41ef86f4e8ef31` | anr / 1 | 51 | `adbaffe` | `android.os.MessageQueue.nativePollOnce` | Idle native sample; request ANR reason/full variants |
| `6b04d11fc291199c08689f982b93deff` | anr / 1 | — | `adbaffe` | `android.os.MessageQueue.nativePollOnce` | Idle native sample; request ANR reason/full variants |
| `6e624b76b57dd44b79bd7de85a6e8413` | anr / 1 | — | `adbaffe` | `__ioctl` | Runtime/framework/SDK sample; full timed trace needed |
| `6e83bebc233b3f9fa6ab1334cc3ab09d` | anr / 1 | — | `adbaffe` | `java.util.concurrent.atomic.AtomicReferenceFieldUpdater$AtomicReferenceFieldUpdaterImpl.get` | Runtime/framework/SDK sample; full timed trace needed |
| `704ea735df6431d83789da52baebe4ad` | anr / 1 | — | `adbaffe` | `android.os.Parcel.recycle` | Runtime/framework/SDK sample; full timed trace needed |
| `71851376d2af5f56955bf728d321d90b` | anr / 1 | 52, 51, 48 | `adbaffe` | `__ioctl` | Runtime/framework/SDK sample; full timed trace needed |
| `7288889e0e9d0fdcf1eaaf3741e69f77` | anr / 1 | — | `adbaffe` | `android.graphics.FrameInfo.markAnimationsStart` | Runtime/framework/SDK sample; full timed trace needed |
| `72deebd901b17347e2c1e907f759a673` | anr / 1 | 51 | `adbaffe` | `__ioctl` | Runtime/framework/SDK sample; full timed trace needed |
| `74f1f30f8bbce7208e426f12ed412764` | anr / 1 | — | `adbaffe` | `androidx.compose.ui.platform.AndroidComposeView.<init>` | App/UI sample; profile timed critical path, cause unproven |
| `7550e6afa3212f28804c49b48b0ccb1b` | anr / 1 | — | `adbaffe` | `region space)] (???` | Runtime/framework/SDK sample; full timed trace needed |
| `779303f416da7f89ac02add5c36eb1e1` | anr / 1 | 51 | `adbaffe` | `com.pairip.licensecheck.LicenseClient.bindToLicensingService` | Measurement/licensing service wait; full service trace needed |
| `7df15e50e6289199b880a50d0b6642f6` | anr / 1 | 51 | `adbaffe` | `art::ConditionVariable::WaitHoldingLocks` | Runtime/framework/SDK sample; full timed trace needed |
| `7e1efa2f4eb40f399816509ade2676de` | crash / 1 | — | `adbaffe` | `android.os.Parcel.readParcelableCreator` | Framework window/input failure; reproduce affected OS before mitigation |
| `80fe7daaa5c14edec79f00e04df7b87d` | anr / 1 | 51 | `adbaffe` | `android::uirenderer::ThreadBase::waitForWork()` | Render/driver wait; capture system trace before changes |
| `860c4fc85fac59324860ba0055b81d3d` | anr / 1 | — | `adbaffe` | `androidx.compose.runtime.Applier.apply` | App/UI sample; profile timed critical path, cause unproven |
| `8752726d50429a924467e711b1c02691` | anr / 1 | — | `adbaffe` | `android.graphics.Bitmap.createBitmap` | Runtime/framework/SDK sample; full timed trace needed |
| `891ef420484aa2dbed41a7437b395397` | anr / 1 | 51 | `adbaffe` | `art::ConditionVariable::WaitHoldingLocks` | Runtime/framework/SDK sample; full timed trace needed |
| `8d782ca9e751c6476994a4d4f4ad0075` | crash / 1 | 52, 51 | `adbaffe` | `android.os.Parcel.createExceptionOrNull` | Framework window/input failure; reproduce affected OS before mitigation |
| `8e75c9bce23072555cb46b7c3f7a638d` | anr / 1 | — | `adbaffe` | `coil.compose.ContentPainterNode.measure-3p2s80s` | Runtime/framework/SDK sample; full timed trace needed |
| `90494fdb9a19b4777730c37a095bf15a` | anr / 1 | — | `adbaffe` | `androidx.compose.runtime.GapComposerKt.findLocation` | App/UI sample; profile timed critical path, cause unproven |
| `90ae1080b8a713316f052b71ccbd62bf` | anr / 1 | — | `adbaffe` | `com.jeerovan.comfer.MainActivityKt.DoubleTapHint` | App/UI sample; profile timed critical path, cause unproven |
| `924617660b314ca9b61c0d0b223787c0` | anr / 1 | — | `adbaffe` | `com.jeerovan.comfer.MainActivityKt.AppListOverlay$updateCenterAppIndex` | 53-11 implemented and locally tested; production unverified |
| `92a0673b900c9dcf74186b6bd72efb33` | anr / 1 | — | `adbaffe` | `androidx.compose.runtime.internal.PersistentCompositionLocalHashMap.access$getEmpty$cp` | App/UI sample; profile timed critical path, cause unproven |
| `9323bc2b4ec14edece4616d9ff84baf3` | anr / 1 | — | `adbaffe` | `androidx.collection.MutableIntObjectMap.c` | Runtime/framework/SDK sample; full timed trace needed |
| `93e8fd12c216a1a86cb03483f1b1db06` | anr / 1 | — | `adbaffe` | `java.lang.String.indexOf` | Runtime/framework/SDK sample; full timed trace needed |
| `94b98bcad1c842463e9d02ab58ce8f46` | anr / 1 | — | `adbaffe` | `eglQuerySurface` | Render/driver wait; capture system trace before changes |
| `95a789cad74b0a3816090ff47a87ec8e` | anr / 1 | — | `adbaffe` | `void std::__1::vector<unsigned int, std::__1::allocator<unsigned int>>::__push_back_slow_path<unsigned int const&>` | Runtime/framework/SDK sample; full timed trace needed |
| `961b98ab1c8e66832d52638f4c0972f7` | anr / 1 | — | `adbaffe` | `com.jeerovan.comfer.AppInfoViewModel.<init>` | 53-10 implemented and locally tested; production unverified |
| `971d84ea4601eceabb126dd1026c3c66` | anr / 1 | — | `adbaffe` | `com.jeerovan.comfer.MainViewModel$onBackButtonPressed$1.invokeSuspend` | App/UI sample; profile timed critical path, cause unproven |
| `984d9dccf04927ad5083962db6f2840f` | anr / 1 | — | `adbaffe` | `android.view.View.getLocationOnScreen` | Runtime/framework/SDK sample; full timed trace needed |
| `9b67401c27eb1fdf4f2926b6deb7c52a` | anr / 1 | — | `adbaffe` | `kotlin.collections.EmptyList.hashCode` | Runtime/framework/SDK sample; full timed trace needed |
| `a1f4abcadd5448f75431cabc3d5a0a31` | anr / 1 | — | `adbaffe` | `com.jeerovan.comfer.ComferApp.setupImageWorker` | 53-07 implemented and locally tested; production unverified |
| `a1f83e1f9e992711abf36f9d9f42fd69` | anr / 1 | 51 | `adbaffe` | `com.jeerovan.comfer.MainActivity.onCreate` | App/UI sample; profile timed critical path, cause unproven |
| `a2fa9517d0bc68deba75386b17cd3c89` | anr / 1 | — | `adbaffe` | `cmpbe_v2_compile_multiple_shaders` | Render/driver wait; capture system trace before changes |
| `a5a9aa779a2a249c69052699e89ae21f` | anr / 1 | 51, 48 | `adbaffe` | `com.jeerovan.comfer.MainActivity.onCreate` | App/UI sample; profile timed critical path, cause unproven |
| `a6f6a380e5f89bca51d125d3b4241e9a` | anr / 1 | — | `adbaffe` | `com.google.firebase.sessions.FirebaseSessionsComponent$MainModule$Companion.sessionConfigsDataStore` | Runtime/framework/SDK sample; full timed trace needed |
| `a93833faac95c810d8e808a119b27274` | anr / 1 | — | `adbaffe` | `android.view.ViewRootImpl.<init>` | Runtime/framework/SDK sample; full timed trace needed |
| `a980b5cafd5f65e7bc678c16cc31ae15` | anr / 1 | — | `adbaffe` | `androidx.compose.animation.core.Transition$DeferredAnimation.getData$animation_core` | App/UI sample; profile timed critical path, cause unproven |
| `aca1452f1eb4504b513d9b7c2a43ce71` | anr / 1 | — | `adbaffe` | `kotlin.coroutines.ContinuationInterceptor$DefaultImpls.get` | Runtime/framework/SDK sample; full timed trace needed |
| `ad16be58153378b1789c8abe9296b65e` | anr / 1 | — | `adbaffe` | `com.jeerovan.comfer.tasks.ComposableSingletons$TasksActivityKt$$ExternalSyntheticLambda2.j` | App/UI sample; profile timed critical path, cause unproven |
| `aefddb6e0370c4f7a2c66b39b0a511e0` | anr / 1 | — | `adbaffe` | `androidx.core.view.WindowInsetsCompat$TypeImpl34.toPlatformType` | Runtime/framework/SDK sample; full timed trace needed |
| `b3d55f92371a2c17e178bb5db095757a` | anr / 1 | — | `adbaffe` | `androidx.compose.runtime.Recomposer$runRecomposeAndApplyChanges$2.invokeSuspend` | App/UI sample; profile timed critical path, cause unproven |
| `b48c91256f0c3f7f918607a55416a7a0` | anr / 1 | — | `adbaffe` | `androidx.compose.foundation.gestures.TapGestureDetectorKt.processTapGesture` | App/UI sample; profile timed critical path, cause unproven |
| `b7b66b919ae5755db5527dad7853aab9` | anr / 1 | — | `adbaffe` | `com.jeerovan.comfer.WidgetHostManager.initHosts` | App/UI sample; profile timed critical path, cause unproven |
| `b84436684d03057c8f7f7ba53868b2bb` | anr / 1 | — | `adbaffe` | `glTexSubImage2D` | Render/driver wait; capture system trace before changes |
| `b8f783e754bd002ebf2b47f81ad8f6a4` | anr / 1 | 51, 48 | `adbaffe` | `art::DumpNativeStack` | Runtime/framework/SDK sample; full timed trace needed |
| `b94db80f3927f84d91d5e0e1a3c55804` | anr / 1 | 51, 48 | `adbaffe` | `android.app.unipnp.UnionManagerComponentFactory.getUniApi` | Runtime/framework/SDK sample; full timed trace needed |
| `b97237913bb97cb287e3627a52da81fb` | anr / 1 | — | `adbaffe` | `pthread_cond_signal` | Runtime/framework/SDK sample; full timed trace needed |
| `b98d9bd012a6238c80021745de58e445` | anr / 1 | — | `adbaffe` | `m7.es.m` | Measurement/licensing service wait; full service trace needed |
| `bb8000739656d1bc583c3afe67b9f5cc` | anr / 1 | — | `adbaffe` | `androidx.profileinstaller.ProfileInstallerInitializer.create` | Runtime/framework/SDK sample; full timed trace needed |
| `bbcb98a20cd09c6b03207ddb0d701ea3` | anr / 1 | — | `adbaffe` | `glDrawRangeElements` | Render/driver wait; capture system trace before changes |
| `bc2ccbdabb44f9178a8848857b11019f` | anr / 1 | — | `adbaffe` | `androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNodeImpl$PointerEventHandlerCoroutine.o` | App/UI sample; profile timed critical path, cause unproven |
| `bd45a06d6613f26ae5f66570745cb1cd` | anr / 1 | 51 | `adbaffe` | `com.mediatek.boostfwk.identify.scroll.ScrollIdentify.inputEventCheck` | Runtime/framework/SDK sample; full timed trace needed |
| `bd879d7d78080be3b166db6e50c00098` | anr / 1 | — | `adbaffe` | `android.os.Handler.dispatchMessage` | Runtime/framework/SDK sample; full timed trace needed |
| `c1d44b212de197434816c43b1fb0e734` | anr / 1 | — | `adbaffe` | `ajhm.<init>` | Runtime/framework/SDK sample; full timed trace needed |
| `c38cede8fb743dbb875662d223be2209` | anr / 1 | — | `adbaffe` | `com.android.internal.telephony.UsimNotifier.isNotifyEnabled` | Runtime/framework/SDK sample; full timed trace needed |
| `c425c7d5d92fb0c71daa10013c3354aa` | anr / 1 | 51 | `adbaffe` | `android.app.Activity.onResume` | Runtime/framework/SDK sample; full timed trace needed |
| `c536c631a8ef56df36ad7efc56a40ac7` | anr / 1 | — | `adbaffe` | `androidx.compose.ui.Modifier$Node.getAggregateChildKindSet$ui` | App/UI sample; profile timed critical path, cause unproven |
| `c893fbed85590888d9766ca59104b341` | anr / 1 | — | `adbaffe` | `com.google.firebase.sessions.FirebaseSessionsComponent$MainModule$Companion.loadDataStoreSharedCounter` | Runtime/framework/SDK sample; full timed trace needed |
| `c9beace58c86a6959b4cd696f67854a2` | anr / 1 | — | `adbaffe` | `coil.compose.UtilsKt.toScale` | Runtime/framework/SDK sample; full timed trace needed |
| `c9c5e0f334b5357db22e18e9a4d82106` | anr / 1 | — | `adbaffe` | `androidx.compose.foundation.AbstractClickableNode$handlePressInteractionCancel$1$1$1.u` | App/UI sample; profile timed critical path, cause unproven |
| `ca287ab996c84b875fddc790c9409329` | anr / 1 | — | `adbaffe` | `com.jeerovan.comfer.WidgetHostManager$startListening$1.invokeSuspend` | Start Binder wait; 51-02 view-thread constraint retained; investigate |
| `ca6c97c85798a0caf5d896e105aaef03` | anr / 1 | — | `adbaffe` | `qh0.startActivityForResult` | Runtime/framework/SDK sample; full timed trace needed |
| `cc1b430562d81495e33e428e36472ca6` | anr / 1 | — | `adbaffe` | `com.jeerovan.comfer.HomeGestureGuideKt.nextHomeGuideStep` | App/UI sample; profile timed critical path, cause unproven |
| `cd8a561caa57f4cced37ae9b94c45c2e` | anr / 1 | — | `adbaffe` | `androidx.compose.ui.graphics.LayerOutsets.equals` | App/UI sample; profile timed critical path, cause unproven |
| `ce663a2fdf273387fdfd89ecff91eed6` | anr / 1 | — | `adbaffe` | `com.pairip.licensecheck.LicenseClient.bindToLicensingService` | Measurement/licensing service wait; full service trace needed |
| `cfa1dc25ac11edc73ae56b57d8d0d3c9` | anr / 1 | — | `unknown` | `art::ConditionVariable::WaitHoldingLocks` | Runtime/framework/SDK sample; full timed trace needed |
| `d22aff7a52e2a7191f3ae59075f894eb` | anr / 1 | 52, 48 | `adbaffe` | `art::ConditionVariable::WaitHoldingLocks` | Runtime/framework/SDK sample; full timed trace needed |
| `d4c6494e8811deebb212436a69fb9b22` | anr / 1 | — | `adbaffe` | `android.view.View.transformMatrixToGlobal` | Runtime/framework/SDK sample; full timed trace needed |
| `d5fd7bb3dd83b6395434a793366022c4` | anr / 1 | — | `adbaffe` | `com.jeerovan.comfer.MyNotificationListenerService.onNotificationPosted` | App/UI sample; profile timed critical path, cause unproven |
| `d61caf27a8152cc3dbb632e42e424ab2` | anr / 1 | — | `adbaffe` | `com.google.firebase.sessions.settings.RemoteSettings.<clinit>` | Runtime/framework/SDK sample; full timed trace needed |
| `d9a63f4e92a37f12754550d1e2e47518` | anr / 1 | — | `adbaffe` | `androidx.compose.runtime.snapshots.SnapshotStateObserver$ObservedScopeMap.a` | App/UI sample; profile timed critical path, cause unproven |
| `da23b9cd528cb82d03f1c399f92d7f95` | anr / 1 | — | `adbaffe` | `android.os.StrictMode.allowThreadDiskReads` | Runtime/framework/SDK sample; full timed trace needed |
| `de33b26a178fe4a18241b272984ede5d` | anr / 1 | — | `adbaffe` | `androidx.collection.MutableScatterMap.g` | Runtime/framework/SDK sample; full timed trace needed |
| `e27e16295f248ba35ac70aad22b7b265` | anr / 1 | — | `adbaffe` | `com.transsion.hubcore.utils.TranClassInfo.getImpl` | Runtime/framework/SDK sample; full timed trace needed |
| `e2d3656b801b549025b8b0115c58aec7` | anr / 1 | 51 | `adbaffe` | `art::ConditionVariable::WaitHoldingLocks` | Runtime/framework/SDK sample; full timed trace needed |
| `e607d6ca0a88fc089b87c95de8301a84` | anr / 1 | — | `adbaffe` | `com.transsion.lice.LiceInfo.getImpl` | Runtime/framework/SDK sample; full timed trace needed |
| `eb5a02e662c79713dce7a19c216d2369` | crash / 1 | — | `adbaffe` | `android.view.SurfaceControl.checkNotReleased` | Framework window/input failure; reproduce affected OS before mitigation |
| `ee1a83b06ca1d7d27a7c364fb2787645` | anr / 1 | — | `adbaffe` | `egl_window_surface_t::swapBuffers()` | Render/driver wait; capture system trace before changes |
| `ef715e1213c320b2739fbc5b0ead0a5f` | anr / 1 | 52, 51, 48 | `adbaffe` | `art::ConditionVariable::WaitHoldingLocks` | Runtime/framework/SDK sample; full timed trace needed |
| `f047956ef162ba1dec7c132de508f500` | anr / 1 | — | `adbaffe` | `com.transsion.hubsdk.trancare.trancareassist.TranTrancareAssistManager.getService` | Runtime/framework/SDK sample; full timed trace needed |
| `f20460f7a3857264d457f4e5450d199e` | anr / 1 | — | `adbaffe` | `androidx.compose.runtime.GapComposer.b0` | App/UI sample; profile timed critical path, cause unproven |
| `f62adb6785b0e9d8e4095d9572fab21f` | anr / 1 | 48 | `adbaffe` | `com.jeerovan.comfer.MainActivityKt$LauncherTwoPaneLayout$1$1.measure-3p2s80s` | App/UI sample; profile timed critical path, cause unproven |
| `f722d5a1b9f922f407a5de5fcf6bea44` | anr / 1 | — | `adbaffe` | `androidx.compose.ui.node.NodeCoordinator.P0` | App/UI sample; profile timed critical path, cause unproven |
| `f73fe5b0a7ac80190b8f441c3a69dde4` | anr / 1 | — | `adbaffe` | `androidx.compose.ui.input.pointer.PointerIcon.<clinit>` | App/UI sample; profile timed critical path, cause unproven |
| `f793c8d4e8822d14529822e84c447952` | anr / 1 | — | `adbaffe` | `com.jeerovan.comfer.utils.CommonUtil.handleStartActivity` | App/UI sample; profile timed critical path, cause unproven |
| `f7f2edd590e457ca5623a77bd90cd14b` | anr / 1 | — | `adbaffe` | `androidx.compose.ui.platform.AndroidComposeViewAccessibilityDelegateCompat$boundsUpdatesEventLoop$1.invokeSuspend` | App/UI sample; profile timed critical path, cause unproven |
| `f8432beb29f1db9b74207a2fb444695c` | anr / 1 | 51, 49, 48 | `adbaffe` | `cmpbe_v2_compile_multiple_shaders` | Render/driver wait; capture system trace before changes |
| `f8f1936b81652440ea84aaf712d628bf` | anr / 1 | — | `adbaffe` | `androidx.compose.ui.contentcapture.AndroidContentCaptureManager.onStop` | App/UI sample; profile timed critical path, cause unproven |
| `fbff4bfdcf6beaec0086dc2fc8bbd06b` | anr / 1 | — | `adbaffe` | `com.vivo.game.IGameManager$Stub$Proxy.isVSRWhitelistApp` | Runtime/framework/SDK sample; full timed trace needed |
| `ffff6f54a55405c572128260dcd8c209` | anr / 1 | 51, 48 | `adbaffe` | `android.os.BinderProxy.transactNative` | Runtime/framework/SDK sample; full timed trace needed |
