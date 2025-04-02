package com.nkd.event.utils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.net.URI;
import java.util.Base64;
import java.util.Random;

@Slf4j
public class CommonUtils {

    public static String getCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(cookieName)) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    public static Cookie generateCookie(String cookieName, String cookieValue, int maxAge) {
        Cookie cookie = new Cookie(cookieName, cookieValue);
        cookie.setMaxAge(maxAge);
        cookie.setPath("/");
        return cookie;
    }

    public static String convertUrlToDataUri(String imageUrl) {
        try (InputStream in = URI.create(imageUrl).toURL().openStream()) {
            byte[] imageBytes = IOUtils.toByteArray(in);
            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            return "data:image/png;base64," + base64;
        } catch (Exception e) {
            log.error("Error converting URL to Data URI: {}", e.getMessage());
            return imageUrl;
        }
    }

    public static String generateRandomString(Boolean numberOnly, Integer length) {
        if (length == null || length <= 0) {
            return "";
        }

        String characters;
        if (numberOnly != null && numberOnly) {
            characters = "0123456789";
        } else {
            characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        }

        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int randomIndex = random.nextInt(characters.length());
            sb.append(characters.charAt(randomIndex));
        }

        return sb.toString();
    }

}
