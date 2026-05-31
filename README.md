# 📦 StockSense

## 📖 Descripción
El proyecto denominado **StockSense** es un sistema híbrido IoT + App Android para el control inteligente de inventario en pequeños y medianos negocios. Una cámara ESP32-CAM instalada en el área de almacén captura imágenes cuando detecta movimiento; el modelo de visión artificial identifica el producto y registra si entró o salió del inventario. Todo queda sincronizado en tiempo real con la app Android, donde el negocio puede ver su stock actualizado, recibir alertas y generar reportes en PDF con un solo tap.

## 🏗️ Arquitectura
El proyecto se desarrollará con la arquitectura **Cliente-Servidor en capas**, la cual consiste en:
*   **Capa IoT:** ESP32-CAM que captura y envía datos vía MQTT.
*   **Capa de Nube:** Firebase Realtime Database que sincroniza la información en tiempo real.
*   **Capa de Inteligencia Artificial:** Modelos ejecutados directamente en el dispositivo Android (on-device).
*   **Capa de Presentación:** Kotlin + Jetpack Compose que muestra el inventario, alertas, gráficas y reportes PDF al usuario final.

## 💻 Pila Tecnológica
*   **Lenguajes de Programación:** Kotlin (App Android) · C++ / Arduino IDE (Firmware ESP32)
*   **Base de Datos:** Firebase Realtime Database (Nube) · Room Database (Local en el dispositivo)
*   **VCS (Control de Versiones):** GitHub
*   **Framework:** Jetpack Compose · Material 3
*   **IDE:** Android Studio

## 📅 Metodología
El proyecto se desarrollará utilizando la metodología ágil **Scrum**, dividido en 4 Sprints:
*   **SPRINT 1 (1 - 12 de junio):** Hardware IoT + Inventario base en tiempo real.
*   **SPRINT 2 (15 - 26 de junio):** Alertas de stock bajo + Historial de movimientos.
*   **SPRINT 3 (29 de junio - 3 de julio):** Inteligencia Artificial + Gráficas interactivas.
*   **SPRINT 4 (13 - 23 de julio):** Reportes PDF + Exportar + Acabados finales.

## ✨ Principales Funcionalidades
1.  **Detección Automática:** Detección de productos con cámara ESP32-CAM y sensor PIR, sin necesidad de registro manual.
2.  **Sincronización en Tiempo Real:** Actualización de inventario instantánea vía Firebase Realtime Database.
3.  **Alertas Inteligentes:** Alertas de stock bajo con predicción de demanda mediante IA (TensorFlow Lite).
4.  **Reportes Automatizados:** Generación automática de reportes PDF mensuales con análisis redactado por Gemini AI.
5.  **Dashboard Interactivo:** Gráficas interactivas de entradas, salidas, distribución por categoría y predicción de demanda.

## 👥 Equipo de Trabajo
*   **Scrum Master:** Rodriguez Esquivel Vanessa Ivonne
*   **Desarrollador 1:** Castañeda Castillo José Manuel *(Sprint 1 y Sprint 3)*
*   **Desarrollador 2:** Islas Cabrera Erick Uriel *(Sprint 2 y Sprint 3)*
*   **Desarrollador 3:** Lara Manzano José Ulises *(Sprint 2 y Sprint 4)*
*   **Desarrollador 4:** Lazaro Martinez Erik Ivan *(Sprint 1 y Sprint 4)*
*   **Desarrollador 5:** Rodriguez Esquivel Vanessa Ivonne *(Sprint 3 y Sprint 4)*