import socket, threading
s = socket.socket()
s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(("127.0.0.1", 25565))
s.listen(5)
print("echo up", flush=True)
def h(c):
    try:
        d = c.recv(4096)
        c.sendall(b"MC-ECHO:" + d)
        c.close()
    except Exception:
        pass
while True:
    c, _ = s.accept()
    threading.Thread(target=h, args=(c,), daemon=True).start()
