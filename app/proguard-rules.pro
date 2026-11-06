# Retrofit & Gson rules
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# Firebase rules
-keep class com.google.firebase.** { *; }

# Models
-keep class com.xsi.inventory.network.** { *; }
-keepclassmembers class com.xsi.inventory.network.** { *; }
