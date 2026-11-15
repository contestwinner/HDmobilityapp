# Peyya MVP — keep Pleos SDK entry points if minify is enabled later.
-keep class ai.pleos.playground.** { *; }

# --- Pleos Playground SDKs (AIDL binders + reflection-based permission lookup) ---
-keep class ai.pleos.** { *; }
-keep interface ai.pleos.** { *; }
-dontwarn ai.pleos.**
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep our gateways readable in crash logs during the contest demo.
-keepnames class com.peyya.app.service.** { *; }
