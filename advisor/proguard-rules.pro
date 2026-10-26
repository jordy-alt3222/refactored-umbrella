# kotlinx.serialization keeps its generated serializers via the library's bundled rules.
# Keep the data model names so a workshop-supplied JSON file keeps matching after shrinking.
-keep class com.serviceadvisor.app.data.** { *; }
# Navigation 3 keys are saved by class name when the back stack is saved (process death).
-keepnames class com.serviceadvisor.app.ui.** implements androidx.navigation3.runtime.NavKey
