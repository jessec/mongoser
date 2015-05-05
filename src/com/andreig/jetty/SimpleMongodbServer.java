/*
 * Copyright Andrei Goumilevski
 * This file licensed under GPLv3 for non commercial projects
 * GPLv3 text http://www.gnu.org/licenses/gpl-3.0.html
 * For commercial usage please contact me
 * gmlvsk2@gmail.com
 *
 */

package com.andreig.jetty;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.servlet.DispatcherType;
import javax.servlet.http.HttpServlet;

import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.IPAccessHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

public class SimpleMongodbServer {

	private static final Logger log = Logger.getLogger(SimpleMongodbServer.class.getName());

	// -------------------------------------------
	public static void main(String[] args) throws Exception {

		Config.init();
		if (Config.search)
			Search.init(Config.search_index_path);

		// server
		Server server = new Server();
		// server.setDumpAfterStart(true);
		server.setSendServerVersion(false);
		QueuedThreadPool tp = new QueuedThreadPool(Config.server_threadsno);

		// connectors
		// ssl
		if (Config.ssl) {

			SslSelectChannelConnector ssl_connector = new SslSelectChannelConnector();
			ssl_connector.setStatsOn(false);
			if (Config.server_adr != null)
				ssl_connector.setHost(Config.server_adr);
			ssl_connector.setPort(Config.server_port);
			ssl_connector.setThreadPool(tp);
			ssl_connector.setName("SimpleMongodb SSL connector");
			SslContextFactory cf = ssl_connector.getSslContextFactory();
			cf.setKeyStorePath(Config.ssl_keystore);
			cf.setKeyStorePassword(Config.ssl_passwd);
			cf.setKeyManagerPassword(Config.ssl_passwd);
			server.setConnectors(new Connector[] { ssl_connector });

		} else {

			SelectChannelConnector connector = new SelectChannelConnector();
			connector.setStatsOn(false);
			if (Config.server_adr != null)
				connector.setHost(Config.server_adr);
			connector.setPort(Config.server_port);
			connector.setThreadPool(tp);
			connector.setName("SimpleMongodb connector");
			server.setConnectors(new Connector[] { connector });

		}

		// HandlerCollection handlers = new HandlerList();
		AbstractHandler _handler;

		ErrorPageErrorHandler error_handler = new MyErrorHandler();
		error_handler.addErrorPage(400, 599, "/err");
		// error_handler.addErrorPage( MyException.class, "/exc" );
		error_handler.setShowStacks(false);

		ServletContextHandler context_handler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
		context_handler.setContextPath("/");
		context_handler.setErrorHandler(error_handler);
		_handler = context_handler;

		// ip filtering
		if (Config.ip_filter) {
			IPAccessHandler ip_handler = new IPAccessHandler();
			if (Config.ip_white != null)
				ip_handler.addWhite(Config.ip_white);
			if (Config.ip_black != null)
				ip_handler.addBlack(Config.ip_black);
			ip_handler.setHandler(context_handler);
			_handler = ip_handler;
		}

		// authentication
		if (Config.auth) {
			ConstraintSecurityHandler sec_handler = new ConstraintSecurityHandler();
			context_handler.setSecurityHandler(sec_handler);

			LoginService login_service = new HashLoginService("Mongodb Realm", "./config/auth.properties");
			context_handler.addBean(login_service);

			Constraint constraint = new Constraint();
			constraint.setName("auth");
			constraint.setAuthenticate(true);
			constraint.setRoles(new String[] { "mongoreadonly", "mongoreadwrite", "admin" });

			ConstraintMapping mapping = new ConstraintMapping();
			mapping.setPathSpec("/*");
			mapping.setConstraint(constraint);

			Set<String> known_roles = new HashSet<String>();
			known_roles.add("user");

			sec_handler.setConstraintMappings(Collections.singletonList(mapping), known_roles);
			sec_handler.setAuthenticator(new BasicAuthenticator());
			sec_handler.setLoginService(login_service);
			sec_handler.setStrict(false);

		}

		server.setHandler(_handler);

		Map<String, HttpServlet> servlets = new HashMap<String, HttpServlet>();
		servlets.put("/database/*", new DatabasesServlet());
		servlets.put("/collection/*", new CollectionsServlet());
		servlets.put("/index/*", new IndexServlet());
		servlets.put("/query/*", new QueryServlet());
		servlets.put("/write/*", new WriteServlet());
		servlets.put("/user/*", new AdminServlet());
		servlets.put("/distinct/*", new DistinctServlet());
		servlets.put("/err", new ErrorServlet());
		if (Config.search)
			servlets.put("/search", new SearchServlet());
		if (Config.gridfs)
			servlets.put("/gridfs/*", new GridfsServlet());
		// servlets.put( "/exc", new MyExceptionServlet() );

		// servlets
		for (String path : servlets.keySet()) {
			context_handler.addServlet(new ServletHolder(servlets.get(path)), path);
		}
		context_handler.addServlet(DefaultServlet.class, "/*");

		// output filter
		FilterHolder out_filter = new FilterHolder(new OutputFilter());
		context_handler.addFilter(out_filter, "/*", EnumSet.of(DispatcherType.REQUEST));

		// input filter
		/*
		 * FilterHolder input_filter = new FilterHolder( new InputFilter() );
		 * context_handler.addFilter( input_filter, "/*",
		 * EnumSet.of(DispatcherType.REQUEST) );
		 */

		Runnable shutdown = new Runnable() {
			public void run() {
				log.info("Shutting server");
				try {
					MongoDB.close();
					if (Config.search)
						Search.close();
				} catch (Exception e) {
					log.severe("Problem closing resources:" + e);
				}
			}
		};
		Runtime.getRuntime().addShutdownHook(new Thread(shutdown));

		server.start();
		server.join();

	}

}
