#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/file.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>
#ifndef N3_HOST_TEST
#include <sys/system_properties.h>
#include <android/log.h>
#endif

#ifndef SO_PEERSEC
#define SO_PEERSEC 31
#endif
#ifdef N3_ROM
#define SOCKET_PATH "/dev/socket/n3-route-v2"
#define PEER_UID 1000
#define PEER_DOMAIN "u:r:system_server:s0"
#define CONTROL_SOCKET_ENV "ANDROID_SOCKET_n3_route_v2"
#else
#define SOCKET_PATH "/data/adb/n3-routing-owned/route.sock"
#define PEER_UID 0
#define PEER_DOMAIN "u:r:ksu:s0"
#endif
#define FINGERPRINT "OnePlus/PLZ110/OP64DDL1:17/CP2A.260605.016/B.2a5e7c5-41b0cd-45d7d3:user/release-keys"
#ifdef N3_HOST_TEST
#define DEADLINE_MS 150
#else
#define DEADLINE_MS 5000
#endif

/* Only trusted internal owner credentials. Product app has NO direct daemon admission. */
static int approved_peer(int fd) {
    struct ucred credentials;
    socklen_t size = sizeof(credentials);
    char domain[256] = {0}; socklen_t length = sizeof(domain) - 1;
    if (getsockopt(fd, SOL_SOCKET, SO_PEERCRED, &credentials, &size) || size != sizeof(credentials)
        || credentials.uid != PEER_UID) return 0;
    if (getsockopt(fd, SOL_SOCKET, SO_PEERSEC, domain, &length) || length >= sizeof(domain)) return 0;
    domain[length] = 0;
    return !strcmp(domain, PEER_DOMAIN);
}
static int64_t now_ms(void) {
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts)) return -1;
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}
static int exact_build(void) {
#ifdef N3_HOST_TEST
    return 1; /* Host fixture only; neither Android endpoint contains this branch. */
#else
    char property[PROP_VALUE_MAX];
    if (__system_property_get("ro.product.device", property) <= 0 || strcmp(property, "dada")) return 0;
    if (__system_property_get("ro.build.fingerprint", property) <= 0 || strcmp(property, FINGERPRINT)) return 0;
    struct stat binary;
    return !lstat("/vendor/bin/mtb", &binary) && S_ISREG(binary.st_mode) && binary.st_uid == 0
        && !(binary.st_mode & 0022);
#endif
}
static int unreaped_child;
/* Fixed diagnostic codes only: never log request payloads, profiles or modem output. */
static int startup_failure(int code, const char *stage) {
    int saved = errno;
#ifndef N3_HOST_TEST
    __android_log_print(ANDROID_LOG_ERROR, "N3Native", "startup failed stage=%s code=%d errno=%d", stage, code, saved);
#endif
    fprintf(stderr, "N3Native startup failed stage=%s code=%d errno=%d\n", stage, code, saved);
    return code;
}
/* Minimal ACK only. Java trusted owner additionally pins the binary SHA256. Never completion. */
static int eid_ack(const char *output) {
    const char *cursor = output;
    while ((cursor = strstr(cursor, "eID=89")) != NULL) {
        if (cursor != output && cursor[-1] != ' ' && cursor[-1] != '\n' && cursor[-1] != ',' && cursor[-1] != ';') { cursor++; continue; }
        const char *digits = cursor + 4; int count = 0;
        while (digits[count] >= '0' && digits[count] <= '9') count++;
        if (count == 32) return 1;
        cursor++;
    }
    return 0;
}
static int select_route(uint32_t destination) {
    if (destination > 1 || !exact_build()) return 0;
    int output[2];
    if (pipe2(output, O_CLOEXEC | O_NONBLOCK)) return 0;
    pid_t child = fork();
    if (child < 0) { close(output[0]); close(output[1]); return 0; }
    if (!child) {
        /* Fixed argv. No shell, reset, profile, GPIO or caller-supplied command. */
        int null = open("/dev/null", O_RDONLY | O_CLOEXEC);
        if (null < 0 || dup2(null, STDIN_FILENO) < 0 || dup2(output[1], STDOUT_FILENO) < 0
            || dup2(output[1], STDERR_FILENO) < 0) _exit(126);
        fcntl(STDOUT_FILENO, F_SETFL, 0);
        close(output[0]); close(output[1]); close(null);
        char *const argv[] = {"/vendor/bin/mtb", "5", "10", destination == 1 ? "1" : "2", "0", NULL};
        char *const env[] = {"PATH=/vendor/bin:/system/bin", "ANDROID_ROOT=/system", "ANDROID_DATA=/data", NULL};
        execve(argv[0], argv, env); _exit(127);
    }
    close(output[1]);
    char bytes[16385]; size_t used = 0; int truncated = 0, exited = 0, eof = 0, status = 0;
    int64_t start = now_ms();
    while (!exited || !eof) {
        int64_t now = now_ms();
        if (now < start || start < 0 || now - start >= DEADLINE_MS) break;
        struct pollfd pfd = {.fd = eof ? -1 : output[0], .events = POLLIN | POLLHUP};
        int wait = (int)(DEADLINE_MS - (now - start));
        if (eof && wait > 25) wait = 25; // wait for exit without spinning on EOF
        if (poll(&pfd, 1, wait) < 0 && errno != EINTR) break;
        char block[1024]; ssize_t count;
        while ((count = read(output[0], block, sizeof(block))) > 0) {
            size_t keep = (size_t)count < 16384 - used ? (size_t)count : 16384 - used;
            memcpy(bytes + used, block, keep); used += keep; truncated |= keep != (size_t)count;
            int64_t read_now = now_ms();
            if (read_now < start || read_now - start >= DEADLINE_MS) break; // continuous output cannot evade deadline
        }
        if (count == 0) eof = 1;
        if (!exited) {
            pid_t result = waitpid(child, &status, WNOHANG);
            if (result == child) exited = 1;
            else if (result < 0 && errno != EINTR) break;
        }
    }
    if (!exited) {
        kill(child, SIGKILL);
        // SIGKILL does not prove an uninterruptible child has exited. Do not block indefinitely
        // or reuse the endpoint if reaping is unresolved; reply ambiguous then terminate owner.
        pid_t reaped = waitpid(child, &status, WNOHANG);
        if (reaped != child) unreaped_child = 1;
    }
    close(output[0]); bytes[used] = 0;
    int reply = 2;
    if (exited && eof && !truncated && WIFEXITED(status) && WEXITSTATUS(status) == 0
        && !memchr(bytes, 0, used)
        && !strcasestr(bytes, "error") && !strcasestr(bytes, "fail")
        && (destination == 0 || eid_ack(bytes))) reply = 1;
    memset(bytes, 0, sizeof(bytes)); // no raw modem/EID logging/persistence
    return reply; // 0 not started, 1 ACK ONLY, 2 ambiguous after launch
}
static int daemon_main(int argc, char **argv) {
    (void)argv;
    if (argc != 1 || getuid() != 0 || !exact_build()) return startup_failure(1, "uid-argv-build");
#ifdef N3_ROM
    // init provisions/binds/labels this fixed socket. Never parse an app-selected pathname.
    // AOSP init Descriptor::Publish sanitizes every non-alphanumeric byte to '_'.
    const char *value = getenv(CONTROL_SOCKET_ENV);
    char *end = NULL;
    if (!value || !*value) return startup_failure(2, "control-socket-env-missing");
    errno = 0;
    long number = strtol(value, &end, 10);
    if (errno || *end || number < 3 || number > 1024) return startup_failure(2, "control-socket-env-invalid");
    int server = (int)number;
    struct sockaddr_un bound; socklen_t length = sizeof(bound);
    struct stat member;
    memset(&bound, 0, sizeof(bound));
    if (getsockname(server, (struct sockaddr *)&bound, &length)) return startup_failure(3, "getsockname");
    if (bound.sun_family != AF_UNIX || length > sizeof(bound)
        || !memchr(bound.sun_path, 0, sizeof(bound.sun_path)) || strcmp(bound.sun_path, SOCKET_PATH))
        return startup_failure(3, "control-socket-address");
    if (lstat(SOCKET_PATH, &member)) return startup_failure(3, "socket-path-lstat");
    if (!S_ISSOCK(member.st_mode) || member.st_uid != 1000 || member.st_gid != 1000 || (member.st_mode & 0777) != 0600)
        return startup_failure(3, "socket-path-metadata");
    int socket_type; socklen_t type_size = sizeof(socket_type);
    if (getsockopt(server, SOL_SOCKET, SO_TYPE, &socket_type, &type_size)
        || type_size != sizeof(socket_type) || socket_type != SOCK_STREAM)
        return startup_failure(3, "socket-fd-type");
    if (fcntl(server, F_SETFD, FD_CLOEXEC)) return startup_failure(3, "socket-cloexec");
#else
    const char *chain[] = {"/", "/data", "/data/adb", "/data/adb/n3-routing-owned"};
    for (size_t i = 0; i < sizeof(chain) / sizeof(chain[0]); i++) {
        struct stat parent;
        if (lstat(chain[i], &parent) || !S_ISDIR(parent.st_mode) || (parent.st_mode & 0002)
            || (parent.st_uid != 0 && parent.st_uid != 1000)
            || ((parent.st_mode & 0020) && parent.st_gid != 0 && parent.st_gid != 1000)) return 2;
        if (i == 3 && (parent.st_uid != 0 || (parent.st_mode & 0777) != 0700)) return 2;
    }
    int lock = open("/data/adb/n3-routing-owned/native.lock", O_CREAT | O_RDWR | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (lock < 0 || flock(lock, LOCK_EX | LOCK_NB)) return 3;
    struct stat existing;
    if (!lstat(SOCKET_PATH, &existing)) {
        if (!S_ISSOCK(existing.st_mode) || existing.st_uid != 0 || unlink(SOCKET_PATH)) return 3;
    } else if (errno != ENOENT) return 3;
    int server = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (server < 0) return 4;
    struct sockaddr_un address = {.sun_family = AF_UNIX};
    strncpy(address.sun_path, SOCKET_PATH, sizeof(address.sun_path) - 1);
    umask(0077);
    if (bind(server, (struct sockaddr *)&address, sizeof(address)) || chmod(SOCKET_PATH, 0600)) return 5;
#endif
    if (listen(server, 4)) return startup_failure(7, "listen");
#ifndef N3_HOST_TEST
    __android_log_write(ANDROID_LOG_INFO, "N3Native", "authenticated init listener ready; no route command on startup");
#endif
    for (;;) {
        int peer = accept4(server, NULL, NULL, SOCK_CLOEXEC);
        if (peer < 0) { if (errno == EINTR) continue; return startup_failure(8, "accept"); }
        struct timeval timeout = {.tv_sec = 1};
        if (setsockopt(peer, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout))
            || setsockopt(peer, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout))) { close(peer); continue; }
        uint32_t request[2]; int result = 0;
        if (approved_peer(peer) && recv(peer, request, sizeof(request), MSG_WAITALL) == sizeof(request)
            && ntohl(request[0]) == 0x4e335232 && ntohl(request[1]) <= 1) result = select_route(ntohl(request[1]));
        uint32_t response = htonl((uint32_t)result);
        send(peer, &response, sizeof(response), MSG_NOSIGNAL); close(peer);
        if (unreaped_child) return 9;
    }
}
#ifndef N3_HOST_TEST
int main(int argc, char **argv) { return daemon_main(argc, argv); }
#endif
