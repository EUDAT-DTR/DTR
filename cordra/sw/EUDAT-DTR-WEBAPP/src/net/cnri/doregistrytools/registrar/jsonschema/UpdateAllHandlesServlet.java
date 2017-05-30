package net.cnri.doregistrytools.registrar.jsonschema;

import java.io.IOException;
import java.io.Writer;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.doregistrytools.registrar.jsonschema.RestartServerServlet.RestartingResponse;

import com.google.gson.Gson;

@WebServlet({"/updateHandles/*"})
public class UpdateAllHandlesServlet extends HttpServlet {
    private Logger logger = LoggerFactory.getLogger(new Object() { }.getClass().getEnclosingClass());

    private RegistrarService registrar;
    private Gson gson;

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            gson = new Gson();
            registrar = RegistrarServiceFactory.getRegistrarService(getServletContext());
        } catch (Exception e) {
            throw new ServletException(e);
        } 
    }
    
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        registrar.updateAllHandleRecords();
        Writer w = response.getWriter();
        w.write("{}");
        w.flush();
        w.close();
    }
    
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AllHandlesUpdater.UpdateStatus status = registrar.getHandleUpdateStatus();
        String json = gson.toJson(status);
        Writer w = response.getWriter();
        w.write(json);
        w.flush();
        w.close();
    }   

}
