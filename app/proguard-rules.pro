# Reglas de ProGuard/R8 propias del proyecto (la versión release se minifica).

# Glance crea las acciones de los widgets (ActionCallback) por reflexión, con el constructor vacío.
# Su propia regla conserva la clase pero no el constructor, y R8 en modo completo lo borra: el botón
# del widget no hace nada.
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    <init>();
}
