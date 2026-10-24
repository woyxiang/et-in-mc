import socket
s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
s.bind(("127.0.0.1", 24454))
print("udp echo up", flush=True)
while True:
    d, a = s.recvfrom(4096)
    s.sendto(b"UDP-ECHO:" + d, a)
