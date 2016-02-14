/*
 * Copyright 2014 otros.systems@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pl.otros.intellij.JumpToCode.server;

import com.google.common.base.Optional;
import org.apache.commons.lang.StringUtils;
import pl.otros.intellij.JumpToCode.logic.FileCopyUtils;
import pl.otros.intellij.JumpToCode.logic.FileUtils;
import pl.otros.intellij.JumpToCode.model.JumpLocation;
import pl.otros.intellij.JumpToCode.model.SourceLocation;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 */
public class JumpToCodeServlet extends HttpServlet {

  private static String getParameter(HttpServletRequest request, String shortName, String longName) {
    String value = request.getParameter(longName);
    if (value == null) {
      value = request.getParameter(shortName);
    }
    return StringUtils.defaultString(value, "");
  }

  private static Optional<String> getOptParameter(HttpServletRequest request, String shortName, String longName) {
    String value = request.getParameter(longName);
    if (value == null) {
      value = request.getParameter(shortName);
    }
    return Optional.fromNullable(value);
  }

  private static String getParameter(HttpServletRequest request, String shortName, String longName, String defaultValue) {
    String value = getParameter(request, shortName, longName);
    return (value != null) ? value : defaultValue;
  }

  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    response.setContentType("text/html");
    response.addHeader("ide", "idea");

    String operation = getParameter(request, "operation", "o", "form");
    if (operation.equals("form")) {
      form(response);
      return;
    }
    if (StringUtils.equalsIgnoreCase("jump", operation)) {
      jump(request, response, false);
    } else if (StringUtils.equalsIgnoreCase("test", operation)) {
      jump(request, response, true);
    } else if (StringUtils.equalsIgnoreCase("content", operation)) {
      content(request, response);
    } else if (StringUtils.equalsIgnoreCase("all", operation)) {
      final String wholeClass = FileUtils.findWholeClass(getParameter(request, "c", "className"));
      response.setContentType("text/plain");
      if (wholeClass.length() > 0) {
        response.getWriter().print(wholeClass);
      } else {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
      }
    }  else {
      error(response, "Unexpected operation");
    }
  }

  private void content(HttpServletRequest request, HttpServletResponse response) throws IOException {
    //TODO add finding by msg
    String packageName = getParameter(request, "p", "packageName");
    String fileName = getParameter(request, "f", "fileName");
    String className = getParameter(request, "c", "className");
    int lineNumber = parseInt(getParameter(request, "l", "lineNumber"), 0);
    SourceLocation location;
    if (packageName != null && fileName != null) {
      location = new SourceLocation(packageName, fileName, lineNumber);
    } else {
      if (className != null) {
        location = new SourceLocation(className);
      } else {
        error(response, "either (packageName,fileName) or (className) is required");
        return;
      }
    }
    String content = FileUtils.getContent(location);

    if (content.length() > 0) {
      response.setStatus(HttpServletResponse.SC_OK);
      response.addHeader("line", Integer.toString(location.getLineNumber()));
      response.getWriter().append(content);
    } else {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  private void jump(HttpServletRequest request, HttpServletResponse response, boolean test) throws IOException {
    final Optional<String> pkg = getOptParameter(request, "p", "packageName");
    final Optional<String> clazz = getOptParameter(request, "c", "className");
    final Optional<String> file = getOptParameter(request, "f", "file");
    final Optional<String> line = getOptParameter(request, "l", "lineNumber");
    final Optional<String> msg = getOptParameter(request, "m", "message");
    final List<JumpLocation> locations = FileUtils.findLocation(pkg, clazz, file, line, msg);

    if (locations.isEmpty()) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND,"Class not found");
      return;
    }

    boolean ok;
    if (test) {
      ok = !locations.isEmpty();
    } else {
      ok = FileUtils.jumpToLocation(locations);
    }
    if (ok) {
      response.setStatus(HttpServletResponse.SC_OK);
      if (test) {
        response.getWriter().println("OK, found " );
      } else {
        response.getWriter().println("OK, jumped to ");
      }
    } else {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      response.getWriter().println("Not found: ");
    }
  }

  private void form(HttpServletResponse response) throws IOException {
    final InputStream formIs = getClass().getClassLoader().getResourceAsStream("form.html");
    FileCopyUtils.copy(formIs, response.getOutputStream());
    response.setStatus(HttpServletResponse.SC_OK);
  }

  private int parseInt(String value, int defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private void error(HttpServletResponse response, String message) throws IOException {
    response.getWriter().println(message);
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
  }
}
