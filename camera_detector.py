
import cv2
import time
import os
import base64
from datetime import datetime
import firebase_admin
from firebase_admin import credentials, db

# ── CONFIGURACIÓN ────────────────────────────────────────────────────
FIREBASE_CREDENTIALS_PATH = "firebase-credentials.json"
FIREBASE_DATABASE_URL = "https://stocksense-4bbaa-default-rtdb.firebaseio.com/"

CAMERA_INDEX = 0          
MOTION_THRESHOLD = 5000   
COOLDOWN_SECONDS = 4      
JPEG_QUALITY = 60         

DESACTIVAR_AUTOENFOQUE = True
VALOR_ENFOQUE_MANUAL = 30   


RAFAGA_NUM_FRAMES = 8
RAFAGA_INTERVALO_SEGUNDOS = 0.07

CAPTURES_FOLDER = "capturas"


RETENCION_HORAS = 72  

LIMPIEZA_CADA_N_CAPTURAS = 5



def inicializar_firebase():
    cred = credentials.Certificate(FIREBASE_CREDENTIALS_PATH)
    firebase_admin.initialize_app(cred, {
        'databaseURL': FIREBASE_DATABASE_URL
    })
    print(" Firebase conectado correctamente")


def configurar_camara(cap):
    
    if not DESACTIVAR_AUTOENFOQUE:
        return
    try:
        cap.set(cv2.CAP_PROP_AUTOFOCUS, 0)
        cap.set(cv2.CAP_PROP_FOCUS, VALOR_ENFOQUE_MANUAL)
        print(f"✓ Autoenfoque desactivado, enfoque manual en {VALOR_ENFOQUE_MANUAL}")
    except Exception as e:
        print(f"  (no se pudo fijar enfoque manual, la cámara podría no soportarlo: {e})")


def imagen_a_base64(ruta_local: str) -> str:
    with open(ruta_local, "rb") as f:
        contenido = f.read()
    return base64.b64encode(contenido).decode("utf-8")


def medir_nitidez(frame) -> float:
    gris = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    return cv2.Laplacian(gris, cv2.CV_64F).var()


def capturar_rafaga(cap, num_frames: int, intervalo: float):
    mejor_frame = None
    mejor_nitidez = -1.0

    for _ in range(num_frames):
        ret, frame = cap.read()
        if not ret:
            continue

        nitidez = medir_nitidez(frame)
        if nitidez > mejor_nitidez:
            mejor_nitidez = nitidez
            mejor_frame = frame

        time.sleep(intervalo)

    return mejor_frame, mejor_nitidez


def registrar_evento(imagen_base64: str, nombre_archivo: str) -> bool:
    try:
        ref = db.reference("imagenes_pendientes")
        nuevo_evento = ref.push()
        nuevo_evento.set({
            "nombreArchivo": nombre_archivo,
            "imagenBase64": imagen_base64,
            "timestamp": int(time.time() * 1000),
            "procesada": False
        })
        print(f" Evento registrado en Firebase ({len(imagen_base64)} caracteres Base64)")
        return True
    except Exception as e:
        print("  Error subiendo a Firebase: {e}")
        return False


def limpiar_imagenes_procesadas_viejas():
    try:
        ahora_ms = int(time.time() * 1000)
        limite_ms = ahora_ms - (RETENCION_HORAS * 60 * 60 * 1000)

        ref = db.reference("imagenes_pendientes")
        todas = ref.get() or {}

        borradas = 0
        for clave, datos in todas.items():
            if not isinstance(datos, dict):
                continue

            esta_procesada = datos.get("procesada", False)
            timestamp = datos.get("timestamp", 0)

            if esta_procesada and timestamp < limite_ms:
                ref.child(clave).delete()
                borradas += 1

        if borradas > 0:
            print(f" Limpieza Firebase: {borradas} imagen(es) procesada(s) de hace más de {RETENCION_HORAS}h eliminadas")
    except Exception as e:
        print(f"   Error durante limpieza de Firebase: {e}")
def main():
    if not os.path.exists(CAPTURES_FOLDER):
        os.makedirs(CAPTURES_FOLDER)

    print("Inicializando Firebase...")
    inicializar_firebase()

    print(f"Abriendo cámara (índice {CAMERA_INDEX})...")
    cap = cv2.VideoCapture(CAMERA_INDEX)

    if not cap.isOpened():
        print(f" ERROR: No se pudo abrir la cámara en índice {CAMERA_INDEX}.")
        print("  Prueba cambiando CAMERA_INDEX a 0, 1 o 2 en el script.")
        return

    configurar_camara(cap)

    print(" Cámara activa")
    print("  Modo: detección de SALIDAS (retiro de producto del almacén).")
    print("Cámara activa, esperando movimiento... (presiona 'q' para salir)")

    frame_anterior = None
    ultima_captura = 0
    contador_capturas = 0

    while True:
        ret, frame = cap.read()
        if not ret:
            print(" ERROR: No se pudo leer el frame de la cámara")
            break

        gris = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        gris = cv2.GaussianBlur(gris, (21, 21), 0)

        if frame_anterior is None:
            frame_anterior = gris
            continue

        diferencia = cv2.absdiff(frame_anterior, gris)
        _, umbral = cv2.threshold(diferencia, 25, 255, cv2.THRESH_BINARY)
        movimiento_detectado = cv2.countNonZero(umbral)

        frame_anterior = gris

        tiempo_actual = time.time()
        en_cooldown = (tiempo_actual - ultima_captura) < COOLDOWN_SECONDS

        estado_texto = "MOVIMIENTO DETECTADO" if movimiento_detectado > MOTION_THRESHOLD else "Esperando..."
        color_texto = (0, 0, 255) if movimiento_detectado > MOTION_THRESHOLD else (0, 255, 0)
        cv2.putText(frame, estado_texto, (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.7, color_texto, 2)
        cv2.imshow("StockSense - Camara IoT (presiona 'q' para salir)", frame)

        if movimiento_detectado > MOTION_THRESHOLD and not en_cooldown:
            print(f"\n→ Movimiento detectado (nivel: {movimiento_detectado})")
            print(f"  Capturando ráfaga de {RAFAGA_NUM_FRAMES} frames...")

            mejor_frame, nitidez = capturar_rafaga(
                cap, RAFAGA_NUM_FRAMES, RAFAGA_INTERVALO_SEGUNDOS
            )

            if mejor_frame is not None:
                timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
                nombre_archivo = f"captura_{timestamp}.jpg"
                ruta_local = os.path.join(CAPTURES_FOLDER, nombre_archivo)

                cv2.imwrite(
                    ruta_local,
                    mejor_frame,
                    [cv2.IMWRITE_JPEG_QUALITY, JPEG_QUALITY]
                )
                print(f"   Foto guardada: {ruta_local} (nitidez: {nitidez:.1f})")

                b64 = imagen_a_base64(ruta_local)
                subida_exitosa = registrar_evento(b64, nombre_archivo)

                if subida_exitosa:
                    try:
                        os.remove(ruta_local)
                        print(f"   Archivo local eliminado: {ruta_local}")
                    except Exception as e:
                        print(f"  No se pudo borrar el archivo local: {e}")

                contador_capturas += 1
                if contador_capturas % LIMPIEZA_CADA_N_CAPTURAS == 0:
                    limpiar_imagenes_procesadas_viejas()
            else:
                print("  No se pudo capturar ningún frame válido en la ráfaga")

            ultima_captura = tiempo_actual

        if cv2.waitKey(1) & 0xFF == ord('q'):
            print("\nCerrando programa...")
            break

    cap.release()
    cv2.destroyAllWindows()


if __name__ == "__main__":
    main()