-keepattributes Signature
-keepattributes *Annotation*
-keep class com.ray.classflow.sync.** { *; }
# Glance identifies widget types by their runtime class names. R8 must not
# merge or rename them, or agenda updates can overwrite timetable widgets.
-keep class com.ray.classflow.widget.ClassFlowWidget { *; }
-keep class com.ray.classflow.widget.TimetableWidget { *; }
-dontwarn org.conscrypt.**

