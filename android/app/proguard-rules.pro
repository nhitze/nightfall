# kotlinx.serialization keeps generated serializers via the plugin.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Compose ships its own consumer rules; no reflection-heavy app code is used.
