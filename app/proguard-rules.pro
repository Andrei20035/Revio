# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# Wire DTOs (Retrofit + kotlinx.serialization) under R8 full mode
# ---------------------------------------------------------------------------
# Retrofit's own shipped rules keep a service method's *declared* return type:
#     -if interface * { @retrofit2.http.* public *** *(...); }
#     -keep,allowoptimization,allowshrinking,allowobfuscation class <3>
# For a `suspend` function the declared return type is `java.lang.Object` — the real
# response type exists only inside the generic signature of the synthetic
# `Continuation<? super Response<T>>` parameter. So `T` survives only if something else
# references it concretely, and R8 full mode strips the signature once `T` is gone.
#
# A response DTO whose fields the app never reads is exactly that case: the admin
# remove-post flow only checks for success and never inspects violationId/postId, so R8
# deleted RemovePostResponseDto outright (its class, $Companion and $$serializer all
# appear in mapping/release/usage.txt). Retrofit then resolved the return type as raw
# Object and failed at call time — before any request left the device — with
# "Unable to create converter for class java.lang.Object", which is why an admin's
# "Remove post" did nothing in release builds while debug worked fine.
#
# kotlinx.serialization's own consumer rules cannot cover this: they are all
# `-if @Serializable class ** -keepclassmembers ...`, i.e. they preserve members of
# classes that survive shrinking rather than keeping the class itself.
#
# These types are the wire contract, resolved reflectively rather than by any call site
# R8 can see, so the whole package is kept — this also covers the other DTOs R8 was
# stripping for the same reason (RevokeResultDto, RevokeAllRequestDto,
# UpdateChallengeTitleRequestDto, UpdateProfilePictureRequest) and any endpoint added
# later whose payload happens not to be read.
-keep class com.revio.social.data.remote.dto.** { *; }