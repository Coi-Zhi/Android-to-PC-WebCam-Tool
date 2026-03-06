import socket
import struct
import cv2
import numpy as np
import pyvirtualcam

def recv_exact(sock, n):
    data = b""
    while len(data) < n:
        chunk = sock.recv(n - len(data))
        if not chunk:
            raise ConnectionError("socket closed")
        data += chunk
    return data

sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.connect(("127.0.0.1", 9000))
print("connected to phone")

try:
    first_frame = True
    cam = None

    while True:
        header = recv_exact(sock, 4)
        (size,) = struct.unpack(">I", header)

        payload = recv_exact(sock, size)

        jpg = np.frombuffer(payload, dtype=np.uint8)
        frame = cv2.imdecode(jpg, cv2.IMREAD_COLOR)
        if frame is None:
            continue

        # OpenCV 係 BGR，pyvirtualcam 要 RGB
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

        if first_frame:
            h, w, _ = rgb.shape
            cam = pyvirtualcam.Camera(width=w, height=h, fps=30)
            print(f"Virtual camera started: {w}x{h} @ 30fps")
            first_frame = False

        cam.send(rgb)
        cam.sleep_until_next_frame()

        cv2.imshow("Android Camera via USB", frame)
        if cv2.waitKey(1) & 0xFF == 27:
            break

except Exception as e:
    print("error:", e)

finally:
    if 'cam' in locals() and cam is not None:
        cam.close()
    sock.close()
    cv2.destroyAllWindows()