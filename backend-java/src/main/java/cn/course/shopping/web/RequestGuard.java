package cn.course.shopping.web;

import cn.course.shopping.common.ApiException;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 曾大君：统一请求边界。不启用 CORS；前端和接口同源部署。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestGuard extends OncePerRequestFilter {
    public static final int MAX_BODY_BYTES = 3 * 1024 * 1024;
    private final ObjectMapper json;
    public RequestGuard(ObjectMapper json) { this.json = json; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; frame-ancestors 'none'");
        try {
            if (!request.getMethod().equals("POST")) { chain.doFilter(request, response); return; }
            if (!"ShopApp".equals(request.getHeader("X-Requested-With"))) throw new ApiException(403, "请求缺少安全标识");
            String origin = request.getHeader("Origin");
            if (origin != null && !origin.equals(request.getScheme() + "://" + request.getHeader("Host")))
                throw new ApiException(403, "不接受跨站请求");
            String type = request.getContentType();
            if (type == null || !type.split(";", 2)[0].strip().equalsIgnoreCase("application/json"))
                throw new ApiException(415, "请求须为 JSON 格式");
            int maximum = request.getRequestURI().equals("/api/a/seller/products") ? 20 * 1024 * 1024 : MAX_BODY_BYTES;
            if (request.getContentLengthLong() > maximum) throw new ApiException(413, "请求内容超过大小限制");
            byte[] body = request.getInputStream().readNBytes(maximum + 1);
            if (body.length == 0 || body.length > maximum) throw new ApiException(413, "请求内容为空或超过大小限制");
            JsonNode data;
            try { data = json.readTree(body); }
            catch (IOException error) { throw new ApiException(400, "JSON 格式错误"); }
            if (data == null || !data.isObject()) throw new ApiException(400, "JSON 须为对象");
            chain.doFilter(new BufferedRequest(request, body), response);
        } catch (ApiException error) {
            response.setStatus(error.status());
            response.setContentType("application/json;charset=UTF-8");
            json.writeValue(response.getOutputStream(), Map.of("error", error.getMessage()));
        }
    }

    private static class BufferedRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        BufferedRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] buffer, int offset, int length) { return input.read(buffer, offset, length); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("仅同步读取"); }
            };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
    }
}
