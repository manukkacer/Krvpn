# KRVPN Privacy Policy

**Effective Date:** [DATE]
**Last Updated:** [DATE]

## 1. Introduction

KRVPN ("we", "our", "us") is committed to protecting your privacy. This Privacy Policy explains how we handle information when you use KRVPN mobile application.

## 2. Information We Collect

### 2.1 No Personal Data Collected
**KRVPN does not collect, store, or transmit any personal data.** We do not require accounts, registration, or login.

### 2.2 Local Data Only
All data remains on your device:
- VPN configurations (encrypted locally)
- App settings and preferences
- Connection logs (optional, stored locally)
- Session history (optional, stored locally)

### 2.3 No Analytics or Tracking
- No Google Analytics, Firebase Analytics, or similar services
- No crash reporting (unless you explicitly enable it)
- No advertising identifiers collected
- No usage statistics sent to any server

## 3. How We Use Data

Since we collect no data, there is no data usage to disclose. All processing happens locally on your device.

## 4. Network Connections

When you use KRVPN to connect to a VPN server:

1. **Your traffic** flows through the VPN tunnel to the server you configured
2. **The VPN server** is operated by your account provider, not by us
3. **We do not operate any VPN servers** - you provide your own configurations
4. **Connectivity checks** may contact:
   - `connectivitycheck.gstatic.com` (Android system)
   - `cloudflare.com` / `detectportal.firefox.com` (standard connectivity tests)
   - Your configured DNS resolvers

These checks carry no personal data and are standard Android VPN behavior.

## 5. Data Storage & Security

### 5.1 Local Encryption
- All configurations encrypted with AES-256-GCM
- Keys derived via PBKDF2 (100,000 iterations)
- Master key stored in Android Keystore (hardware-backed when available)
- Field-level locking available for sensitive config fields

### 5.2 Data Deletion
- All data removed when you uninstall the app
- Clear data option in Android Settings removes everything
- No cloud backup of sensitive data (excluded via backup rules)

## 6. Third-Party Services

KRVPN may open external links to:
- **Telegram** (for community/support) - their privacy policy applies
- **GitHub** (for source code) - their privacy policy applies
- **Your VPN provider's website** - their privacy policy applies

We are not responsible for third-party privacy practices.

## 7. Permissions

| Permission | Purpose |
|------------|---------|
| `INTERNET` | VPN tunnel traffic |
| `ACCESS_NETWORK_STATE` | Detect network changes |
| `FOREGROUND_SERVICE` | Keep tunnel alive |
| `FOREGROUND_SERVICE_SPECIAL_USE` | VPN tunnel foreground service |
| `POST_NOTIFICATIONS` | Connection status notifications |
| `WAKE_LOCK` | Prevent sleep during connection |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Keep tunnel alive in background |
| `BIND_VPN_SERVICE` | Create VPN tunnel (system) |

## 8. Children's Privacy

KRVPN is not intended for children under 13. We do not knowingly collect data from children.

## 9. Changes to This Policy

We may update this policy. Changes will be posted in the app and on our GitHub repository. Continued use constitutes acceptance.

## 10. Contact

**Email:** privacy@krvpn.example.com
**Telegram:** @KRVPN_Official
**GitHub:** https://github.com/your-org/krvpn

## 11. Open Source Licenses

KRVPN includes third-party open-source software. See LICENSES.md for full list.

---

**By using KRVPN, you agree to this Privacy Policy.**
