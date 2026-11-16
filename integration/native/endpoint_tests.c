/* NEW host-only fixture. Does not execute mtb, run daemon_main or contact any device. */
#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#define N3_HOST_TEST
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <stdio.h>
#include <signal.h>
static int mode, checks, repeated_reap;
static pid_t reaped_pid;
/* Deliberately synthetic protocol token; construct it without a published identifier. */
#define TEST_EID "89" "1111111111" "1111111111" "1111111111"
static void emit(const char *bytes, size_t size) {
    while (size) {
        ssize_t count = write(1, bytes, size);
        if (count <= 0) _exit(123);
        bytes += count; size -= (size_t)count;
    }
}
static pid_t fixture_waitpid(pid_t pid, int *status, int options) {
    if (pid == reaped_pid) repeated_reap++;
    pid_t result = waitpid(pid, status, options);
    if (result == pid) reaped_pid = pid;
    return result;
}
static int fixture_execve(const char *path, char *const argv[], char *const env[]) {
    (void)env;
    if (strcmp(path, "/vendor/bin/mtb") || strcmp(argv[0], path) || strcmp(argv[1], "5")
        || strcmp(argv[2], "10") || (strcmp(argv[3], "1") && strcmp(argv[3], "2"))
        || strcmp(argv[4], "0") || argv[5] != NULL) _exit(125);
    if (mode == 4) { close(1); close(2); for (;;) pause(); }
    if (mode == 5) { char output[1024]; memset(output, 'x', sizeof(output)); for (;;) emit(output, sizeof(output)); }
    if (mode == 3) { char output[17000]; memset(output, 'x', sizeof(output)); emit(output, sizeof(output)); }
    if (mode == 7) { const char bytes[] = "eID=" TEST_EID "\0error"; emit(bytes, sizeof(bytes)); _exit(0); }
    if (mode == 1) emit("missing ACK", 11);
    else if (mode == 2) emit("error", 5);
    else emit("eID=" TEST_EID "\n", 37);
    _exit(mode == 6 ? 1 : 0);
}
#define waitpid fixture_waitpid
#define execve fixture_execve
#include "route_endpoint.c"
#undef waitpid
#undef execve
static void check(int truth, const char *message) {
    checks++; if (!truth) { fprintf(stderr, "FAIL %s\n", message); exit(1); }
}
static void run(int test_mode, int destination, int expected) {
    mode = test_mode; reaped_pid = -1; repeated_reap = 0; unreaped_child = 0;
    int64_t before = now_ms(); int result = select_route((uint32_t)destination);
    check(result == expected, "classification");
    check(now_ms() - before < 1000, "hard bound including EOF/flood/hung fixture");
    check(!repeated_reap, "no waitpid after successful reap");
    // Fixture cleanup only. Production stops admissions on unresolved reaping instead.
    if (unreaped_child) { int status; while (waitpid(-1, &status, 0) > 0) {} }
}
int main(void) {
    (void)daemon_main; // compile it, do not start any socket endpoint
    check(eid_ack("eID=" TEST_EID), "exact synthetic ACK");
    check(!eid_ack("xeID=" TEST_EID), "embedded prefix rejected");
    check(!eid_ack("eID=" TEST_EID "1"), "too many digits rejected");
    run(0, 1, 1); run(0, 0, 1); run(1, 1, 2); run(2, 1, 2); run(3, 1, 2);
    run(4, 1, 2); run(5, 1, 2); run(6, 1, 2); run(0, 2, 0);
    run(7, 1, 2);
    int sockets[2]; check(!socketpair(AF_UNIX, SOCK_STREAM, 0, sockets), "fixture socketpair");
    check(!approved_peer(sockets[0]), "host UID/domain cannot qualify as approved Android owner");
    close(sockets[0]); close(sockets[1]);
    printf("PASS %d NEW native endpoint assertions; synthetic fixtures only\n", checks);
    return 0;
}
