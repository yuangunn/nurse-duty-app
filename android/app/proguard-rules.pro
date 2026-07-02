# kotlinx-serialization — Wear Data Layer payloads and JSON backup are (de)serialized at runtime
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.nurseduty.**$$serializer { *; }
-keepclassmembers class com.nurseduty.** { *** Companion; }
-keepclasseswithmembers class com.nurseduty.** { kotlinx.serialization.KSerializer serializer(...); }
