# Vendor binding

[简体中文](vendor-binding.md)

After you link a saved SSH server to a vendor, you can use that vendor’s API for power actions even without an SSH session. Credentials are stored on **that server’s binding**, stored in local.

## Prerequisites and entry

1. Add and save the SSH server in the list first.
2. Leave multi-select mode (the bind entry is hidden while multi-select is on).
3. Open the server’s overflow menu and choose **Link vendor**.
4. Pick the vendor and fill in the fields below. The app saves the binding only after the vendor accepts the credentials and, when required, you pick an instance.

## Limits

- A server can be linked to **one** vendor at a time. Unlink first if you want a different vendor.
- Unlinking removes only this server’s vendor link. SSH host, port, and login credentials stay.
- If the vendor rejects the request or the network fails, nothing is saved.

After a successful bind, the same overflow menu offers start, stop, restart, power status, and unlink (some vendors also offer server info; follow the in-app menu).

## Independent vendor APIs

These vendors use their own client-area or KiwiVM-style APIs. After you pick a vendor, enter that vendor’s secrets, then (if asked) choose an instance that is not already linked.

### BandwagonHost

The in-app option name is **BandwagonHost** (not “搬瓦工” or HOST).

Enter:

- **VEID**
- **API key**

Copy these from KiwiVM / the control panel. The same VEID cannot be linked to another SSH server. After verification, the binding is stored on the current server.

### CloudCone

Enter:

- **API Key**
- **Hash**

These keys apply to **this bind** and are stored on this server. There is no global CloudCone account under Settings. After the vendor accepts the keys, choose an instance that is not already linked.

### GreenCloud

The in-app option name is **GreenCloud**.

Enter:

- **API Token** (client-area API)

Each bind asks for the token again; tokens from other servers are not reused. After the vendor accepts it, choose an instance that is not already linked to another SSH server.

## Control-panel vendors

These vendors use a panel API such as Virtualizor. Host and secrets are entered **for the current server** and are not auto-filled from another server’s binding.

### Virtualizor

Enter:

- **Panel host**
- **Panel port**
- **API Key**
- **API Pass**

If the system does not trust the panel certificate, the app asks whether to trust the fingerprint **for that host and port**. After the panel accepts the credentials, choose a VPS that is not already linked to another server.
