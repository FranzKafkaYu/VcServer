#include <jni.h>
#include <cerrno>
#include <cstring>
#include <string>
#include <vector>

#include <arpa/inet.h>
#include <netdb.h>
#include <poll.h>
#include <sys/socket.h>
#include <unistd.h>

namespace {

struct Handle {
	int fd = -1;
	sockaddr_storage dest {};
	socklen_t destLen = 0;
};

Handle *from(jlong ptr) {
	return reinterpret_cast<Handle *>(ptr);
}

int waitReadable(int fd, jint timeoutMs) {
	pollfd pfd {};
	pfd.fd = fd;
	pfd.events = POLLIN;
	int n = poll(&pfd, 1, timeoutMs);
	if (n <= 0) {
		return n;
	}
	return (pfd.revents & POLLIN) ? 1 : 0;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_franzkafkayu_vcserver_mosh_MoshUdp_nativeOpen(JNIEnv *env, jobject, jstring host, jint port) {
	if (host == nullptr || port <= 0 || port > 65535) {
		return 0;
	}
	const char *hostChars = env->GetStringUTFChars(host, nullptr);
	if (hostChars == nullptr) {
		return 0;
	}
	addrinfo hints {};
	hints.ai_socktype = SOCK_DGRAM;
	hints.ai_family = AF_UNSPEC;
	addrinfo *res = nullptr;
	std::string portStr = std::to_string(port);
	int rc = getaddrinfo(hostChars, portStr.c_str(), &hints, &res);
	env->ReleaseStringUTFChars(host, hostChars);
	if (rc != 0 || res == nullptr) {
		return 0;
	}
	int fd = socket(res->ai_family, SOCK_DGRAM, 0);
	if (fd < 0) {
		freeaddrinfo(res);
		return 0;
	}
	auto *h = new Handle();
	h->fd = fd;
	memcpy(&h->dest, res->ai_addr, res->ai_addrlen);
	h->destLen = static_cast<socklen_t>(res->ai_addrlen);
	freeaddrinfo(res);
	return reinterpret_cast<jlong>(h);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_franzkafkayu_vcserver_mosh_MoshUdp_nativeSend(JNIEnv *env, jobject, jlong ptr, jbyteArray packet) {
	Handle *h = from(ptr);
	if (h == nullptr || h->fd < 0 || packet == nullptr) {
		return -1;
	}
	jsize len = env->GetArrayLength(packet);
	std::vector<jbyte> buf(static_cast<size_t>(len));
	env->GetByteArrayRegion(packet, 0, len, buf.data());
	ssize_t sent = sendto(h->fd, buf.data(), static_cast<size_t>(len), 0,
		reinterpret_cast<sockaddr *>(&h->dest), h->destLen);
	return sent < 0 ? -1 : static_cast<jint>(sent);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_franzkafkayu_vcserver_mosh_MoshUdp_nativeRecv(JNIEnv *env, jobject, jlong ptr, jint timeoutMs) {
	Handle *h = from(ptr);
	if (h == nullptr || h->fd < 0) {
		return nullptr;
	}
	int ready = waitReadable(h->fd, timeoutMs);
	if (ready <= 0) {
		return nullptr;
	}
	uint8_t buf[2048];
	sockaddr_storage fromAddr {};
	socklen_t fromLen = sizeof(fromAddr);
	ssize_t n = recvfrom(h->fd, buf, sizeof(buf), 0, reinterpret_cast<sockaddr *>(&fromAddr), &fromLen);
	if (n <= 0) {
		return nullptr;
	}
	if (fromLen > 0) {
		h->dest = fromAddr;
		h->destLen = fromLen;
	}
	jbyteArray out = env->NewByteArray(static_cast<jsize>(n));
	if (out == nullptr) {
		return nullptr;
	}
	env->SetByteArrayRegion(out, 0, static_cast<jsize>(n), reinterpret_cast<jbyte *>(buf));
	return out;
}

extern "C" JNIEXPORT void JNICALL
Java_com_franzkafkayu_vcserver_mosh_MoshUdp_nativeClose(JNIEnv *, jobject, jlong ptr) {
	Handle *h = from(ptr);
	if (h == nullptr) {
		return;
	}
	if (h->fd >= 0) {
		close(h->fd);
	}
	delete h;
}
