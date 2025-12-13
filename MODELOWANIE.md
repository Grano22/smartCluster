* Przerywana - zwrotny
* ... - środki sterowania

        handler.addServletWithMapping(
            new ServletHolder(
                new HttpServlet() {
                    @Override
                    protected void doGet(
                        HttpServletRequest req,
                        HttpServletResponse resp
                    ) throws IOException {
                        resp.setContentType("text/plain");
                        resp.getWriter().println("Handled via HttpServlet!");
                        resp.getWriter().println(req.getRemoteAddr());
                    }
                }
            ),
            "/*"
        );

        server.start();
        server.join();

        server.setHandler((request, response, callback) -> {
            response
                .getHeaders()
                .put("Content-Type", "text/plain; charset=utf-8");
            callback.succeeded(
                Content.of("Handled via lambda!", StandardCharsets.UTF_8)
            );
            return true;
        });
new Handler.Abstract() {
                @Override
                public boolean handle(
                    Request request,
                    Response response,
                    Callback callback
                ) throws Exception {
                    System.out.println("Im asked damn http chukcy mucky");

                    response.setStatus(200);
                    response.write(
                        true,
                        ByteBuffer.wrap(
                            "Hello magic boy!".getBytes(
                                Charset.defaultCharset()
                            )
                        ),
                        callback
                    );

                    return true;
                }
            }
