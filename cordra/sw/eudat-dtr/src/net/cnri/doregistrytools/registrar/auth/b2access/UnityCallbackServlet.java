package net.cnri.doregistrytools.registrar.auth.b2access;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import net.cnri.doregistrytools.registrar.jsonschema.RegistrarService;
import net.cnri.doregistrytools.registrar.jsonschema.RegistrarServiceFactory;
import net.cnri.doregistrytools.registrar.jsonschema.ServletErrorUtil;
import net.cnri.repository.RepositoryException;
import net.cnri.doregistrytools.registrar.auth.RegistrarAuthenticator;

import com.google.gson.Gson;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.google.common.collect.ImmutableMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.codec.binary.Base64;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import javax.net.ssl.SSLContext;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.methods.CloseableHttpResponse;


@WebServlet({"/oauth/unity/callback"})
public class UnityCallbackServlet extends HttpServlet {

    private static Logger logger = LoggerFactory.getLogger(UnityCallbackServlet.class);

    private static RegistrarService registrar;
    private static RegistrarAuthenticator authenticator;

    private Gson gson;

    private String accessToken;

    @Override
    public void init() throws ServletException {
        super.init();

        try {
            gson = new Gson();
            registrar = RegistrarServiceFactory.getRegistrarService(getServletContext());
            authenticator = registrar.getAuthenticator();
        } catch (Exception e) {
            throw new ServletException(e);
        } 
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        //System.out.println("  " + req);

        if(req.getParameter("error") != null) {

            if (req.getParameter("error").equals("access_denied")){

                // redirect the user to the homepage but set a cookie so that
                // we are aware that an authentication error happened
                Cookie foo = new Cookie("oauth2-error", req.getParameter("error_description"));
                foo.setPath("/");
                resp.addCookie(foo);
                resp.sendRedirect("/");
                return;
            }

            resp.getWriter().println(req.getParameter("error"));
            return;
        }

        // Unity returns a short-lived, one-time authorization code that can 
        // be exchanged for an access token
        String authCode = req.getParameter("code");

        // get Unity's access token
        Map<String, String> map = getAccessToken(authCode);

        accessToken = map.get("access_token");

        map = getUserinfo(accessToken);

        boolean authenticated;

        try {
            // move this to function
            String username = map.get("email");
            authenticated = authenticator.authenticateRemote(username, req, resp);
        } catch (RepositoryException e) {
            logger.error("Exception in GET /oauth/unity/callback", e);
            ServletErrorUtil.internalServerError(resp);
            return;
        }

        // redirect the user to the homepage
        resp.sendRedirect("/");
    }

    /* helpers */
    private static Map<String, String> getAccessToken(String authCode){

        HttpPost post = new HttpPost(UnityConstants.tokenEndpoint);

        Map<String, String> map = new HashMap<String, String>();

        String clientId = UnityConstants.clientId;
        String clientSecret = UnityConstants.clientSecret;

        post.setHeader("Authorization", "Basic " + encodeCredentials(clientId, clientSecret));

        List<BasicNameValuePair> parametersBody = new ArrayList<BasicNameValuePair>();

        parametersBody.add(new BasicNameValuePair("code", authCode));
        parametersBody.add(new BasicNameValuePair("redirect_uri", UnityConstants.callbackUri));
        parametersBody.add(new BasicNameValuePair("grant_type", "authorization_code"));

        try {

            post.setEntity(new UrlEncodedFormEntity(parametersBody, HTTP.UTF_8));

            CloseableHttpClient httpclient = createHttpClient(UnityConstants.allowSelfSignedCert);

            CloseableHttpResponse response = httpclient.execute(post);

            int code = response.getStatusLine().getStatusCode();

            // XXX do something with the status code
            map = handleResponse(response);

            httpclient.close();
            response.close();

		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
        }

		return map;
    }

    private static Map<String, String> getUserinfo(String accessToken) {

        HttpGet get = new HttpGet(UnityConstants.userinfoEndpoint); 
        Map<String, String> map = new HashMap<String, String>();

        get.setHeader("Authorization", "Bearer " + accessToken);

        try {

            CloseableHttpClient httpclient = createHttpClient(UnityConstants.allowSelfSignedCert);

            CloseableHttpResponse response = httpclient.execute(get);

            int code = response.getStatusLine().getStatusCode();

            // XXX do something with the status code

            map = handleResponse(response);

            httpclient.close();
            response.close();

		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException(e.getMessage());
        }

		return map;
    }

    private static String encodeCredentials(String username, String password) {

        String cred = username + ":" + password;
		String encodedValue = null;
		byte[] encodedBytes = Base64.encodeBase64(cred.getBytes());
		encodedValue = new String(encodedBytes);

		byte[] decodedBytes = Base64.decodeBase64(encodedBytes);

		return encodedValue;
    }

	/**
	 * Handles the Server response. Delegates to appropriate handler
	 */
	private static Map handleResponse(HttpResponse response) {

		String contentType = "application/json";

		if (response.getEntity().getContentType() != null) {
			contentType = response.getEntity().getContentType().getValue();
		}
		if (contentType.contains("application/json")) {
			return handleJsonResponse(response);
		} else if (contentType.contains("application/x-www-form-urlencoded")) {
			return handleURLEncodedResponse(response);
		} else if (contentType.contains("application/xml")) {
			return handleXMLResponse(response);
		} else {
			// Unsupported Content type
			throw new RuntimeException(
					"Cannot handle "
							+ contentType
							+ " content type. Supported content types include JSON, XML and URLEncoded");
		}

	}

	private static Map handleJsonResponse(HttpResponse response) {
		Map<String, String> oauthLoginResponse = null;
		// String contentType =
		// response.getEntity().getContentType().getValue();
		try {
			oauthLoginResponse = (Map<String, String>) new JSONParser()
					.parse(EntityUtils.toString(response.getEntity()));
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException();
		} catch (Exception e) {
			System.out.println("Could not parse JSON response");
			throw new RuntimeException(e.getMessage());
		}
		//System.out.println();
		//System.out.println("********** Response Received **********");
		//for (Map.Entry<String, String> entry : oauthLoginResponse.entrySet()) {
		//	System.out.println(String.format("  %s = %s", entry.getKey(),
		//			entry.getValue()));
		//}
		return oauthLoginResponse;
	}

	private static Map handleURLEncodedResponse(HttpResponse response) {
		Map<String, Charset> map = Charset.availableCharsets();
		Map<String, String> oauthResponse = new HashMap<String, String>();
		Set<Map.Entry<String, Charset>> set = map.entrySet();
		Charset charset = null;
		HttpEntity entity = response.getEntity();

		//System.out.println();
		//System.out.println("********** Response Received **********");

		//for (Map.Entry<String, Charset> entry : set) {
		//	System.out.println(String.format("  %s = %s", entry.getKey(),
		//			entry.getValue()));
		//	if (entry.getKey().equalsIgnoreCase(HTTP.UTF_8)) {
		//		charset = entry.getValue();
		//	}
		//}

		try {
			List<NameValuePair> list = URLEncodedUtils.parse(
					EntityUtils.toString(entity), Charset.forName(HTTP.UTF_8));
			for (NameValuePair pair : list) {
				System.out.println(String.format("  %s = %s", pair.getName(),
						pair.getValue()));
				oauthResponse.put(pair.getName(), pair.getValue());
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException("Could not parse URLEncoded Response");
		}

		return oauthResponse;
	}

	private static Map handleXMLResponse(HttpResponse response) {
		Map<String, String> oauthResponse = new HashMap<String, String>();
		try {

			String xmlString = EntityUtils.toString(response.getEntity());
			DocumentBuilderFactory factory = DocumentBuilderFactory
					.newInstance();
			DocumentBuilder db = factory.newDocumentBuilder();
			InputSource inStream = new InputSource();
			inStream.setCharacterStream(new StringReader(xmlString));
			Document doc = db.parse(inStream);

			System.out.println("********** Response Receieved **********");
			parseXMLDoc(null, doc, oauthResponse);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(
					"Exception occurred while parsing XML response");
		}
		return oauthResponse;
	}

    private static void parseXMLDoc(Element element, Document doc,
            Map<String, String> oauthResponse) {
        NodeList child = null;
        if (element == null) {
            child = doc.getChildNodes();

        } else {
            child = element.getChildNodes();
        }
        for (int j = 0; j < child.getLength(); j++) {
            if (child.item(j).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                org.w3c.dom.Element childElement = (org.w3c.dom.Element) child
                    .item(j);
                if (childElement.hasChildNodes()) {
                    System.out.println(childElement.getTagName() + " : "
                            + childElement.getTextContent());
                    oauthResponse.put(childElement.getTagName(),
                            childElement.getTextContent());
                    parseXMLDoc(childElement, null, oauthResponse);
                }

            }
        }
    }

    private static CloseableHttpClient createHttpClient(boolean allowSelfSignedCertificates) throws Exception {

        if (allowSelfSignedCertificates) {
            // Trust own CA and all self-signed certs
            SSLContext sslcontext = SSLContexts.custom()
                .loadTrustMaterial(new File(UnityConstants.clientKeystore),
                        UnityConstants.clientKeystorePassword.toCharArray(),
                        new TrustSelfSignedStrategy())
                .build();

            // allow TLSv1 only
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
                    sslcontext, 
                    new String[] { "TLSv1" },
                    null,
                    SSLConnectionSocketFactory.getDefaultHostnameVerifier());

            return HttpClients.custom()
                .setSSLSocketFactory(sslsf)
                .build();
        }

        return HttpClients.createDefault();
    }

    @SuppressWarnings("unused")
    private static class AuthResponse {
        boolean success = false;
        String userId;
        String username;
        
        public AuthResponse(boolean success, String userId, String username) {
            this.success = success;
            this.userId = userId;
            this.username = username;
        }
    }

}
