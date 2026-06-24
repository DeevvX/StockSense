` 📦 StockSense`

## 📖 Descripción
El proyecto denominado StockSense es un sistema híbrido IoT + Aplicación Android para el control inteligente de inventario en pequeños y medianos negocios. Una cámara Logitech C920 conectada a una PC o laptop se instala en el área de almacén y, mediante un script en Python con OpenCV, detecta movimiento cuando un producto es retirado. Al ocurrir este evento, se captura automáticamente una imagen que es enviada a la nube. La aplicación Android recibe la imagen y utiliza Inteligencia Artificial para identificar el producto retirado, registrar la salida y actualizar el inventario en tiempo real.

Adicionalmente, el sistema implementa el registro automático de entradas, donde el usuario puede tomar una fotografía de la nota, factura o recibo del proveedor. La IA analiza el documento, identifica los productos y cantidades recibidas, y registra automáticamente las nuevas entradas en la base de datos, creando productos nuevos si no existen. Todo el sistema se sincroniza en tiempo real con la aplicación móvil, donde el negocio puede consultar su stock actualizado, recibir alertas y generar reportes en PDF con un solo tap.


## 🏗️ Arquitectura
El proyecto se desarrollará con la arquitectura **Cliente-Servidor en capas**, la cual consiste en:
*   **Capa IoT:** Cámara Logitech C920 conectada a una PC/laptop ejecutando un script en Python con OpenCV para detección de movimiento y captura de imágenes.
*   **Capa de Nube:**  Firebase Realtime Database y Firebase Storage para sincronización de inventario e imágenes en tiempo real.
*   **Capa de Inteligencia Artificial:** OpenAI Vision API para identificación de productos y análisis de notas o recibos; OpenAI API para generación de reportes.
*   **Capa de Presentación:** Aplicación Android desarrollada en Kotlin con Jetpack Compose para mostrar inventario, alertas, gráficas y reportes PDF.

## 💻 Pila Tecnológica
*   **Lenguajes de Programación:** Kotlin (App Android) · Python (Script IoT)
*   **Base de Datos:** Firebase Realtime Database (Nube) · Room Database (Local en el dispositivo)
*   **VCS (Control de Versiones):** GitHub
*   **Framework:** Jetpack Compose · Material 3
*   **IDE:** Android Studio

      # 📅 Metodología
El proyecto se desarrollará utilizando la metodología ágil **Scrum**, dividido en 4 Sprints:
*   **SPRINT 1 (1 - 12 de junio):** Base Android, conexión a Firebase y script IoT con detección de movimiento.
*   **SPRINT 2 (15 - 26 de junio):** Alertas de stock bajo, historial de movimientos e integración de IA para identificación.
*   **SPRINT 3 (29 de junio - 3 de julio):** Gráficas interactivas y predicción de demanda.
*   **SPRINT 4 (13 - 23 de julio):** Reportes PDF automatizados, exportación y acabados finales.

## ✨ Principales Funcionalidades
1.  **Detección Automática:** Registro automático de salidas de inventario mediante detección de movimiento con cámara IoT y registro automático de entradas mediante fotografía de notas, facturas o recibos del proveedor.
2.  **Sincronización en Tiempo Real:** Identificación de productos con Inteligencia Artificial a partir de imágenes capturadas.
3.  **Alertas Inteligentes:** Alertas de stock bajo con predicción de demanda mediante IA.
4.  **Reportes Automatizados:** Alertas y reportes automatizados con generación de reportes PDF desde la app.
5.  **Dashboard Interactivo:** Gráficas interactivas de entradas, salidas, distribución por categoría y predicción de demanda.

## 👥 Equipo de Trabajo
*   **Scrum Master:** Rodriguez Esquivel Vanessa Ivonne
*   **Desarrollador 1:** Castañeda Castillo José Manuel 
*   **Desarrollador 2:** Islas Cabrera Erick Uriel 
*   **Desarrollador 3:** Lara Manzano José Ulises 
*   **Desarrollador 4:** Lazaro Martinez Erik Ivan 
*   **Desarrollador 5:** Rodriguez Esquivel Vanessa Ivonne 
