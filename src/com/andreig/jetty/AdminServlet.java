/*
 * Copyright Andrei Goumilevski
 * This file licensed under GPLv3 for non commercial projects
 * GPLv3 text http://www.gnu.org/licenses/gpl-3.0.html
 * For commercial usage please contact me
 * gmlvsk2@gmail.com
 *
*/

package com.andreig.jetty;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.mongodb.DB;
import com.mongodb.WriteResult;

@SuppressWarnings("serial")
@WebServlet(name="AdminServlet")
public class AdminServlet extends SkeletonMongodbServlet {

  private static final Logger log = Logger.getLogger( AdminServlet.class.getName() );

  // --------------------------------
  @Override
  public void init() throws ServletException{

    @SuppressWarnings("unused")
	ServletConfig config = getServletConfig();
    String name = getServletName();
    log.fine( "init() "+name );

  }

  // --------------------------------
  @Override
  public void destroy(){

    @SuppressWarnings("unused")
	ServletConfig config = getServletConfig();
    String name = getServletName();
    log.fine( "destroy() "+name );

  }

  // DELETE
  // ------------------------------------
  @Override
  protected void doDelete(HttpServletRequest req, HttpServletResponse res)
    throws ServletException, IOException {

    log.fine( "doDelete()" );

    if( !can_admin(req) ){
      res.sendError( SC_UNAUTHORIZED );
      return;
    }

    String db_name = req.getParameter( "dbname" );
    String user = req.getParameter( "user" );
    if( db_name==null || user==null ){
      error( res, SC_BAD_REQUEST, Status.get("param name missing") );
      return;
    }

    DB db = mongo.getDB( db_name );
    WriteResult o = db.removeUser( user );

    out_str( req, o.toString(), "application/json" );

  }

  // PUT
  // ------------------------------------
  @Override
  protected void doPut(HttpServletRequest req, HttpServletResponse res)
    throws ServletException, IOException {

    log.fine( "doPut()" );

    if( !can_admin(req) ){
      res.sendError( SC_UNAUTHORIZED );
      return;
    }

    String db_name = req.getParameter( "dbname" );
    String user = req.getParameter( "user" );
    String passwd = req.getParameter( "passwd" );
    if( db_name==null || user==null || passwd==null ){
      error( res, SC_BAD_REQUEST, Status.get("param name missing") );
      return;
    }
    boolean read_only = Boolean.parseBoolean( req.getParameter("readonly") );

    DB db = mongo.getDB( db_name );
    WriteResult o = db.addUser( user, passwd.toCharArray(), read_only );

    out_str( req, o.toString(), "application/json" );

  }


}
