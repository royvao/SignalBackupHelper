# Signal Backup Helper

App Android desarrollada en **Kotlin + Jetpack Compose** para automatizar la copia del **último backup de Signal** a una carpeta destino, lista para sincronizar con FolderSync → Mega (o cualquier nube).

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-API%2026%2B-green.svg)](https://developer.android.com)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-1.6.0-orange.svg)](https://developer.android.com/compose)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## ✨ **Características**

- ✅ **Copia inteligente**: Detecta automáticamente el backup **más reciente** de Signal (`signal.*.backup`).
- 📱 **UI moderna**: Jetpack Compose con navegación lateral, progreso en tiempo real (GB restantes + círculo).
- ⏰ **Backup automático**: WorkManager programa copias diarias a la **hora exacta** que elijas (ej. 03:00).
- 💾 **Persistencia**: Guarda carpetas seleccionadas y configuración entre sesiones.
- 🚀 **No bloquea UI**: Copia archivos grandes (~28GB) en background sin ANR.
- 🔄 **Limpieza automática**: Borra backups anteriores en destino, mantiene **solo el último**.

## 📱 **Uso rápido**

1. Menú → Inicio
2. "Elegir carpeta de origen" → carpeta Backups de Signal
3. "Elegir carpeta de destino" → carpeta para FolderSync
4. "Procesar ahora" → copia el último backup (con barra de progreso)
5. Menú → Configuración → elige hora + activa "Backup diario" → ¡Listo!


## 🛠 **Flujo automático completo**

Signal crea backups → App copia solo el último → 
FolderSync sincroniza con Mega → ¡Siempre tienes el backup más reciente en la nube!

## 📂 **Capturas**

*(Añade screenshots aquí cuando las tengas)*

[Pantalla principal]    [Progreso copia]     [Menú lateral]       [Configuración]
┌─────────────────┐    ┌─────────────────┐   ┌─────────────────┐  ┌─────────────────┐
│ 📁 Origen       │    │ 🔄 Procesando   │   │ ≡ Menú          │  │ ⏰ 03:00 ON     │
│ 📁 Destino      │    │ Restante: 12GB  │   │ -  Inicio        │  │ [Guardar]       │
│ [Procesar]      │    │ ○○○○○○○○○○○○   │   │ -  Configuración │  └─────────────────┘
└─────────────────┘    └─────────────────┘   └─────────────────┘

## 🚀 **Instalación**

1. Clona el repo:
   git clone https://github.com/tuusuario/SignalBackupHelper.git
   cd SignalBackupHelper

2. Abre en **Android Studio** (E:\Dev\AndroidProjects\).

3. Sincroniza Gradle → **Run** en tu móvil.

## 🔧 **Dependencias clave**

| Librería | Versión | Uso |
|----------|---------|-----|
| WorkManager | 2.9.0 | Backup diario automático |
| Jetpack Compose | 1.6.0 | UI moderna y fluida |
| DocumentFile | SAF | Acceso seguro a carpetas |

## 📋 **Configuración FolderSync (recomendada)**

Local folder: carpeta_destino_seleccionada
Remote folder: /SignalBackups en Mega
Direction: To remote folder (solo subida)
Filters: ^signal.*\.backup$
Overwrite old files: ✓

## ⚙️ **Próximas mejoras planeadas**

- [ ] **Historial** real con fechas, tamaños y estado.
- [ ] **Notificaciones** cuando termine el backup (manual/automático).
- [ ] **Selector de hora** más elegante (TimePicker).
- [ ] **Estadísticas**: espacio usado, tiempo medio de copia.
- [ ] **Tema oscuro/claro** automático.

## 📄 **Licencia**

MIT License - ¡Usa, modifica y comparte libremente!

© 2025 LambdaR. Hecho con ❤️(IA) para automatizar backups de Signal.

---

**⭐ Si te sirve, ¡dale una estrella!** 
