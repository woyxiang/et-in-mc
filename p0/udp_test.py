import socket
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.settimeout(6)
s.sendto(b"hello", ("127.0.0.1", 34454))
try:
    data, addr = s.recvfrom(1024)
    print("recv:", data)
except Exception as e:
    print("ERR:", type(e).__name__, e)
