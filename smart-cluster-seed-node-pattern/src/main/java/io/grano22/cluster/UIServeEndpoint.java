package io.grano22.cluster;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

public class UIServeEndpoint extends HttpServlet {
    private static final Map<String, String> FILES = Map.of(
        "/", "static/index.html",
        "/index.html", "static/index.html",
        "/main.js", "static/main.js",
        "/main.css", "static/main.css"
    );

    private static final Map<String, String> CONTENT_TYPES = Map.of(
        "html", "text/html; charset=UTF-8",
        "js", "application/javascript; charset=UTF-8",
        "css", "text/css; charset=UTF-8"
    );

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException {
        String path = Optional.ofNullable(request.getPathInfo()).orElse("/");

        String resourcePath = FILES.get(path);

        try {
            if (resourcePath == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            try (InputStream targetResource = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (targetResource == null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }

                String extension = resourcePath.substring(resourcePath.lastIndexOf(".") + 1);
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType(CONTENT_TYPES.getOrDefault(extension, "application/octet-stream"));
                targetResource.transferTo(response.getOutputStream());
            }

        } catch (IOException exception) {
            throw new ServletException("Cannot process request");
        }
    }
}
