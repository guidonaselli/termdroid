#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <unistd.h>
#include <ctype.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#define SERVER_PORT 8765
#define BUFFER_SIZE 8192

static const char b64_table[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

static char *b64_encode(const char *in, int in_len) {
    int out_len = 4 * ((in_len + 2) / 3);
    char *out = (char *)malloc(out_len + 1);
    if (!out) return NULL;
    int i = 0, j = 0;
    while (i < in_len) {
        uint32_t a = (unsigned char)in[i++];
        uint32_t b = i < in_len ? (unsigned char)in[i++] : 0;
        uint32_t c = i < in_len ? (unsigned char)in[i++] : 0;
        uint32_t triple = (a << 16) + (b << 8) + c;

        out[j++] = b64_table[(triple >> 18) & 0x3F];
        out[j++] = b64_table[(triple >> 12) & 0x3F];
        out[j++] = (i > in_len + 1) ? '=' : b64_table[(triple >> 6) & 0x3F];
        out[j++] = (i > in_len) ? '=' : b64_table[triple & 0x3F];
    }
    out[j] = '\0';
    return out;
}

static void b64_decode_print(const char *in) {
    int val = 0, valb = -8;
    int len = strlen(in);
    for (int i = 0; i < len; i++) {
        char c = in[i];
        if (c == '=' || c == '\r' || c == '\n') break;
        const char *p = strchr(b64_table, c);
        if (!p) continue;
        val = (val << 6) + (int)(p - b64_table);
        valb += 6;
        if (valb >= 0) {
            putchar((char)((val >> valb) & 0xFF));
            valb -= 8;
        }
    }
    fflush(stdout);
}

static void print_banner(const char *name, const char *color_code) {
    printf("\033[%sm╭──────────────────────────────────────────────────╮\033[0m\n", color_code);
    printf("\033[%sm│ %-48s │\033[0m\n", color_code, name);
    printf("\033[%sm│ Escribe tu consulta o 'exit' / 'q' para salir.    │\033[0m\n", color_code);
    printf("\033[%sm╰──────────────────────────────────────────────────╯\033[0m\n\n", color_code);
}

static int execute_query(const char *provider, const char *prompt) {
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) {
        fprintf(stderr, "Error creando socket local.\n");
        return 1;
    }

    struct sockaddr_in server_addr;
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(SERVER_PORT);
    inet_pton(AF_INET, "127.0.0.1", &server_addr.sin_addr);

    if (connect(sock, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0) {
        fprintf(stderr, "\033[31m[!] El servicio de agente de Termdroid no esta respondiendo en el puerto %d.\033[0m\n", SERVER_PORT);
        fprintf(stderr, "Asegurate de que Termdroid este abierto.\n");
        close(sock);
        return 1;
    }

    char *b64_prompt = b64_encode(prompt, strlen(prompt));
    if (!b64_prompt) {
        close(sock);
        return 1;
    }

    // Formato: RUN <PROVIDER> <B64_PROMPT>\n
    char header[256];
    snprintf(header, sizeof(header), "RUN %s ", provider);
    send(sock, header, strlen(header), 0);
    send(sock, b64_prompt, strlen(b64_prompt), 0);
    send(sock, "\n", 1, 0);
    free(b64_prompt);

    char buffer[BUFFER_SIZE];
    char line_buf[BUFFER_SIZE];
    int line_idx = 0;
    int bytes_read;
    int in_thinking = 0;

    while ((bytes_read = recv(sock, buffer, sizeof(buffer) - 1, 0)) > 0) {
        for (int i = 0; i < bytes_read; i++) {
            char ch = buffer[i];
            if (ch == '\n') {
                line_buf[line_idx] = '\0';
                
                if (strncmp(line_buf, "T:", 2) == 0) {
                    if (in_thinking) {
                        printf("\033[0m\n");
                        in_thinking = 0;
                    }
                    b64_decode_print(line_buf + 2);
                } else if (strncmp(line_buf, "K:", 2) == 0) {
                    if (!in_thinking) {
                        printf("\033[90m[pensando...] ");
                        in_thinking = 1;
                    }
                    b64_decode_print(line_buf + 2);
                } else if (strncmp(line_buf, "R:", 2) == 0) {
                    if (in_thinking) {
                        printf("\033[0m\n");
                        in_thinking = 0;
                    }
                    printf("\n\033[33m⚡ Tool: %s\033[0m\n", line_buf + 2);
                    fflush(stdout);
                } else if (strncmp(line_buf, "F:", 2) == 0) {
                    printf("\033[32m✔ %s\033[0m\n", line_buf + 2);
                    fflush(stdout);
                } else if (strncmp(line_buf, "E:", 2) == 0) {
                    if (in_thinking) {
                        printf("\033[0m\n");
                        in_thinking = 0;
                    }
                    printf("\n\033[31m[Error] %s\033[0m\n", line_buf + 2);
                    fflush(stdout);
                } else if (strncmp(line_buf, "D:", 2) == 0) {
                    if (in_thinking) {
                        printf("\033[0m\n");
                        in_thinking = 0;
                    }
                    printf("\n");
                    fflush(stdout);
                }

                line_idx = 0;
            } else if (line_idx < (int)sizeof(line_buf) - 1) {
                line_buf[line_idx++] = ch;
            }
        }
    }

    if (in_thinking) {
        printf("\033[0m\n");
    }

    close(sock);
    return 0;
}

static void run_login(const char *provider) {
    const char *home = getenv("HOME");
    if (!home) home = "/data/data/com.termdroid/files/home";

    if (strcmp(provider, "CLAUDE") == 0) {
        printf("===========================================\n");
        printf(" 🟣 Claude Pro / Team Login\n");
        printf("===========================================\n");
        printf("Abriendo tu navegador (Brave) para autenticar...\n");
        system("am start -a android.intent.action.VIEW -d \"https://claude.ai\" >/dev/null 2>&1");
        printf("\n1. Inicia sesion en Claude en tu navegador.\n");
        printf("2. Copia tu sessionKey o token de sesion.\n");
        printf("3. Pegalo aqui abajo:\nToken / sessionKey: ");
        
        char token[1024];
        if (fgets(token, sizeof(token), stdin)) {
            char *p = token + strlen(token) - 1;
            while (p >= token && (*p == '\n' || *p == '\r' || isspace(*p))) *p-- = '\0';
            if (strlen(token) > 0) {
                char cmd[2048];
                snprintf(cmd, sizeof(cmd), "mkdir -p \"%s/.claude\" && echo '{\"sessionKey\":\"%s\",\"provider\":\"anthropic\"}' > \"%s/.claude.json\"", home, token, home);
                system(cmd);
                printf("\n✅ Autenticado correctamente con Claude!\nGuardado en ~/.claude.json y sincronizado con Termdroid.\n");
            } else {
                printf("Cancelado.\n");
            }
        }
    } else if (strcmp(provider, "OPENAI") == 0 || strcmp(provider, "CODEX") == 0) {
        printf("===========================================\n");
        printf(" 🟢 Codex / ChatGPT Login\n");
        printf("===========================================\n");
        printf("Abriendo tu navegador (Brave) para autenticar...\n");
        system("am start -a android.intent.action.VIEW -d \"https://chatgpt.com/api/auth/session\" >/dev/null 2>&1");
        printf("\n1. Inicia sesion en ChatGPT en tu navegador.\n");
        printf("2. Copia tu token de sesion de ChatGPT.\n");
        printf("3. Pegalo aqui abajo:\nToken: ");

        char token[1024];
        if (fgets(token, sizeof(token), stdin)) {
            char *p = token + strlen(token) - 1;
            while (p >= token && (*p == '\n' || *p == '\r' || isspace(*p))) *p-- = '\0';
            if (strlen(token) > 0) {
                char cmd[2048];
                snprintf(cmd, sizeof(cmd), "mkdir -p \"%s/.codex\" && echo '{\"accessToken\":\"%s\",\"provider\":\"openai\"}' > \"%s/.codex/auth.json\"", home, token, home);
                system(cmd);
                printf("\n✅ Autenticado correctamente con Codex / ChatGPT!\nGuardado en ~/.codex/auth.json y sincronizado con Termdroid.\n");
            } else {
                printf("Cancelado.\n");
            }
        }
    } else {
        printf("===========================================\n");
        printf(" 🔵 Gemini / AGY Login\n");
        printf("===========================================\n");
        printf("Ingresa tu API Key de Gemini / Google AI Studio:\nAPI Key: ");
        char token[1024];
        if (fgets(token, sizeof(token), stdin)) {
            char *p = token + strlen(token) - 1;
            while (p >= token && (*p == '\n' || *p == '\r' || isspace(*p))) *p-- = '\0';
            if (strlen(token) > 0) {
                char cmd[2048];
                snprintf(cmd, sizeof(cmd), "mkdir -p \"%s/.gemini\" && echo '{\"apiKey\":\"%s\",\"provider\":\"gemini\"}' > \"%s/.gemini/auth.json\"", home, token, home);
                system(cmd);
                printf("\n✅ Autenticado correctamente con Gemini / AGY!\n");
            } else {
                printf("Cancelado.\n");
            }
        }
    }
}

static int run_install(void) {
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) {
        fprintf(stderr, "Error creando socket local.\n");
        return 1;
    }

    struct sockaddr_in server_addr;
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(SERVER_PORT);
    inet_pton(AF_INET, "127.0.0.1", &server_addr.sin_addr);

    if (connect(sock, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0) {
        fprintf(stderr, "\033[31m[!] El servicio de Termdroid no esta respondiendo.\033[0m\n");
        close(sock);
        return 1;
    }

    printf("===========================================\n");
    printf(" 📦 Instalador de Node.js y CLIs Oficiales\n");
    printf("===========================================\n");

    send(sock, "INSTALL\n", 8, 0);

    char buffer[BUFFER_SIZE];
    char line_buf[BUFFER_SIZE];
    int line_idx = 0;
    int bytes_read;

    while ((bytes_read = recv(sock, buffer, sizeof(buffer) - 1, 0)) > 0) {
        for (int i = 0; i < bytes_read; i++) {
            char ch = buffer[i];
            if (ch == '\n') {
                line_buf[line_idx] = '\0';
                if (strncmp(line_buf, "T:", 2) == 0) {
                    b64_decode_print(line_buf + 2);
                    printf("\n");
                } else if (strncmp(line_buf, "E:", 2) == 0) {
                    printf("\033[31m[Error] %s\033[0m\n", line_buf + 2);
                } else if (strncmp(line_buf, "D:", 2) == 0) {
                    printf("\n");
                }
                line_idx = 0;
            } else if (line_idx < (int)sizeof(line_buf) - 1) {
                line_buf[line_idx++] = ch;
            }
        }
    }
    close(sock);

    printf("\n⚙️ Instalando Node.js, npm y CLIs oficiales...\n");
    const char *prefix = getenv("PREFIX");
    if (!prefix) prefix = "/data/data/com.termdroid/files/usr";

    char cmd[2048];
    snprintf(cmd, sizeof(cmd), "\"%s/bin/alpine-sh\" -c \"apk add --no-cache nodejs npm git && npm install -g @anthropic-ai/claude-code @openai/codex\"", prefix);
    int res = system(cmd);

    if (res == 0) {
        printf("\n===========================================\n");
        printf("✅ ¡Instalacion completada con exito!\n");
        printf("Escribe 'claude' o 'codex' para iniciar sesion.\n");
        printf("===========================================\n");
    } else {
        printf("\n===========================================\n");
        printf("⚠️ Hubo advertencias o errores durante la instalacion.\n");
        printf("===========================================\n");
    }
    return res;
}

int main(int argc, char **argv) {
    const char *cmd_name = "claude";
    if (argc > 0 && argv[0]) {
        char *slash = strrchr(argv[0], '/');
        cmd_name = slash ? slash + 1 : argv[0];
    }

    const char *provider = "CLAUDE";
    const char *cli_title = "🟣 Claude Code CLI";
    const char *color = "35;1";
    const char *prompt_prefix = "\033[35;1mclaude>\033[0m ";

    int arg_start = 1;
    if (argc > 1 && (strcmp(argv[1], "claude") == 0 || strcmp(argv[1], "codex") == 0 || 
                     strcmp(argv[1], "gemini") == 0 || strcmp(argv[1], "agy") == 0)) {
        cmd_name = argv[1];
        arg_start = 2;
    }

    if (strstr(cmd_name, "codex") != NULL) {
        provider = "OPENAI";
        cli_title = "🟢 Codex CLI";
        color = "32;1";
        prompt_prefix = "\033[32;1mcodex>\033[0m ";
    } else if (strstr(cmd_name, "gemini") != NULL || strstr(cmd_name, "agy") != NULL) {
        provider = "GEMINI";
        cli_title = "🔵 Gemini / AGY CLI";
        color = "34;1";
        prompt_prefix = "\033[34;1magy>\033[0m ";
    }

    if (argc > arg_start && (strcmp(argv[arg_start], "login") == 0 || strcmp(argv[arg_start], "auth") == 0)) {
        run_login(provider);
        return 0;
    }

    if (argc > arg_start && (strcmp(argv[arg_start], "install") == 0 || strcmp(argv[arg_start], "setup") == 0 || strcmp(argv[arg_start], "setup-node") == 0)) {
        return run_install();
    }

    if (argc > arg_start) {
        char full_prompt[BUFFER_SIZE];
        full_prompt[0] = '\0';
        for (int i = arg_start; i < argc; i++) {
            if (i > arg_start) strncat(full_prompt, " ", sizeof(full_prompt) - strlen(full_prompt) - 1);
            strncat(full_prompt, argv[i], sizeof(full_prompt) - strlen(full_prompt) - 1);
        }
        return execute_query(provider, full_prompt);
    }

    print_banner(cli_title, color);

    char input_line[BUFFER_SIZE];
    while (1) {
        fputs(prompt_prefix, stdout);
        fflush(stdout);

        if (!fgets(input_line, sizeof(input_line), stdin)) {
            printf("\n");
            break;
        }

        char *p = input_line + strlen(input_line) - 1;
        while (p >= input_line && (*p == '\n' || *p == '\r' || isspace(*p))) *p-- = '\0';

        if (strlen(input_line) == 0) continue;
        if (strcmp(input_line, "exit") == 0 || strcmp(input_line, "quit") == 0 || strcmp(input_line, "q") == 0) {
            printf("Hasta luego!\n");
            break;
        }
        if (strcmp(input_line, "login") == 0 || strcmp(input_line, "auth") == 0) {
            run_login(provider);
            continue;
        }

        execute_query(provider, input_line);
        printf("\n");
    }

    return 0;
}
