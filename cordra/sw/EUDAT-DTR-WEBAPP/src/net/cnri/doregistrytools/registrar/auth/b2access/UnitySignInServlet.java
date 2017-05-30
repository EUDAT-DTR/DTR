package net.cnri.doregistrytools.registrar.auth.b2access;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.security.Key;
import net.cnri.doregistrytools.registrar.jsonschema.ServletErrorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebServlet({"/oauth/unity/login"})
public class UnitySignInServlet extends HttpServlet {

    private static Logger logger = LoggerFactory.getLogger(UnitySignInServlet.class);

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

        try {
            String url = getAuthorizationUrl();
            resp.sendRedirect(url);
        } catch (Exception e) {
            logger.error("Exception in GET /oauth/unity/login", e);
            ServletErrorUtil.internalServerError(resp);
            return;
        }
    }

    /* helpers */
    private String getAuthorizationUrl() throws Exception {

        String csrfToken = UnityConstants.createSecureToken();

        StringBuilder url = new StringBuilder()
            .append(UnityConstants.authorizationEndpoint)
            .append("?client_id=").append(UnityConstants.clientId)
            .append("&response_type=code")
            .append("&state=").append(csrfToken)
            .append("&scope=GENERATE_USER_CERTIFICATE+USER_PROFILE")
            .append("&redirect_uri=").append(UnityConstants.callbackUri);

        //System.out.println("*** redirect_uri=" + UnityConstants.callbackUri);

        return url.toString();
    }

}
