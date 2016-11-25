package net.cnri.doregistrytools.registrar.auth.b2access;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet({"/oauth/unity/login"})
public class UnitySignInServlet extends HttpServlet {

    @Override
    public void init() throws ServletException {
        super.init();

        try {
            UnityConstants.readUnityConfig();
        } catch (Exception e) {
        }

    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {


        String url = getAuthorizationUrl();
        resp.sendRedirect(url);
    }

    /* helpers */
    private String getAuthorizationUrl() {

        StringBuilder url = new StringBuilder()
            .append(UnityConstants.authorizationEndpoint)
            .append("?client_id=").append(UnityConstants.clientId)
            .append("&response_type=code")
            .append("&scope=GENERATE_USER_CERTIFICATE+USER_PROFILE")
            .append("&redirect_uri=").append(UnityConstants.callbackUri);

        System.out.println("*** redirect_uri=" + UnityConstants.callbackUri);

        return url.toString();
    }
}
