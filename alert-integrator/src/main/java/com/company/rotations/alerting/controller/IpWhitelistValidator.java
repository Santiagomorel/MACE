package com.company.rotations.alerting.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;

@Component
public class IpWhitelistValidator {

    private static final Logger logger = LoggerFactory.getLogger(IpWhitelistValidator.class);

    private final List<String> allowedIpPatterns;
    private final boolean enabled;

    public IpWhitelistValidator(
            @Value("${app.providers.gitguardian.allowed-ips:}") String ipList,
            @Value("${app.providers.gitguardian.enabled:true}") boolean enabled) {
        this.allowedIpPatterns = ipList != null && !ipList.isBlank()
                ? List.of(ipList.split(","))
                : List.of();
        this.enabled = enabled && !allowedIpPatterns.isEmpty();
    }

    public boolean isAllowed(HttpServletRequest request, String source) {
        if (!enabled) {
            logger.debug("IP whitelist disabled for source: {}", source);
            return true;
        }

        String clientIp = extractClientIp(request);
        if (clientIp == null || clientIp.isBlank()) {
            logger.warn("Could not determine client IP for source: {}", source);
            return false;
        }

        boolean allowed = allowedIpPatterns.stream()
                .anyMatch(pattern -> matchesPattern(clientIp, pattern));

        if (!allowed) {
            logger.warn("IP {} blocked by whitelist for source: {}", clientIp, source);
        } else {
            logger.debug("IP {} allowed by whitelist for source: {}", clientIp, source);
        }
        return allowed;
    }

    private String extractClientIp(HttpServletRequest request) {
        String[] ipHeaders = {"X-Forwarded-For", "X-Real-IP", "CF-Connecting-IP"};
        for (String header : ipHeaders) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isBlank()) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private boolean matchesPattern(String clientIp, String pattern) {
        if (pattern.contains("/")) {
            return matchesCidr(clientIp, pattern);
        }
        return clientIp.equals(pattern);
    }

    private boolean matchesCidr(String clientIp, String cidr) {
        try {
            String[] parts = cidr.split("/");
            String networkIp = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);

            InetAddress clientAddr = InetAddress.getByName(clientIp);
            InetAddress networkAddr = InetAddress.getByName(networkIp);

            byte[] clientBytes = clientAddr.getAddress();
            byte[] networkBytes = networkAddr.getAddress();

            int fullBytes = prefixLength / 8;
            int partialBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (clientBytes[i] != networkBytes[i]) return false;
            }

            if (partialBits > 0) {
                int mask = (0xFF << (8 - partialBits)) & 0xFF;
                if ((clientBytes[fullBytes] & mask) != (networkBytes[fullBytes] & mask)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            logger.error("Error matching IP {} against CIDR {}", clientIp, cidr, e);
            return false;
        }
    }
}
