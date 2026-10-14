# Corporate Agent API

**Separate** backend for the Corporate / Bank Islami **Agent Android app**.  
Does **not** live inside DFS-Corporate or Bank-Islami-Corporate.

## How it works (your model)

```
┌─────────────┐     login / registration      ┌──────────────────────┐
│ Agent App   │ ─────────────────────────────►│ Corporate Agent API  │  ← YOU manage
│ (Android)   │ ◄─────────────────────────────│  JWT + agent store   │
└──────┬──────┘                               └──────────┬───────────┘
       │  IBFT / W2W / bills / cash                     │
       │                                                │ when DFS_PROXY_ENABLED=true
       │                                                ▼
       │                                      ┌──────────────────────┐
       │                                      │ DFS (:18001 / :18009)│
       │                                      └──────────┬───────────┘
       │◄──────────── same DFS JSON response ────────────┘
```

| Area | Who owns it |
|------|-------------|
| Login, device OTP, registration | **This API** |
| Balance, MPIN, mini-statement, QR | **This API** (local wallet for now) |
| IBFT, W2W, bills, cash-in/out | **App → this API → DFS → app** (or stub if proxy off) |

## Run

```powershell
cd D:\Karsaaz\Corporate-Agent-API
mvn spring-boot:run
```

- Port: **8092**
- Health: http://localhost:8092/health

### Demo agent (seeded)

| Field | Value |
|-------|--------|
| Mobile / username | `03001234567` |
| MPIN / password | `1234` |

## Android app config

Point **all** Agent base URLs at this service (one host is enough for stubs):

```
DFS_BASE_URL            = http://<your-deploy-host>:8092/
DFS_TXN_BASE_URL        = http://<your-deploy-host>:8092/
DFS_THIRD_PARTY_BASE_URL= http://<your-deploy-host>:8092/
DFS_CARD_BASE_URL       = http://<your-deploy-host>:8092/   # card stubs not yet
```

On deploy set (recommended — same auth as Corporate portal):

```powershell
$env:PUBLIC_BASE_URL="https://your-domain.example"
$env:DFS_PROXY_ENABLED="true"
$env:DFS_PROXY_MODE="portal"
$env:CORPORATE_PORTAL_API_KEY="<same key as portal>"
$env:DFS_PORTAL_API_CHANNEL="COP"
$env:DFS_TXN_API_BASE_URL="http://46.225.160.93:18009/transactions"
$env:DFS_AGENT_BASE_URL="http://46.225.160.93:18001"
$env:DFS_TXN_BASE_URL="http://46.225.160.93:18009"
$env:DFS_THIRD_PARTY_BASE_URL="http://46.225.160.93:18008"
```

App keeps calling `/transactions/v1/*` on this API; we map to DFS `/v1/corporate/*` + `X-Portal-Key`.

Legacy bearer mode (DFS agent JWT): `DFS_PROXY_MODE=bearer` and optional `DFS_UPSTREAM_BEARER`.

## Implemented endpoints

### Managed here (auth)
- `POST /agentapp/v1/login`
- `POST /agentapp/v1/updateDevice`
- `POST /agentapp/v1/verifyUpdateDevice`
- `POST /agentapp/v1/agentDeviceRegistration`
- `POST /agentapp/v1/verifyAgentdeviceRegistration`
- `GET  /agentapp/v1/getAccountUpgradeLovs`

### Managed here (wallet)
- `POST /agentapp/v1/getbalance`
- `POST /agentapp/v1/viewlimits`
- `POST /agentapp/v1/changempin`
- `POST /agentapp/v1/miniStatment`
- `POST /agentapp/v1/decodeqr`
- `POST /agentapp/v1/generateQr`

### Gateway → DFS (or stub)
- `GET  /transactions/v1/getAllBank`
- `GET  /transactions/v1/getbiller`
- `POST /transactions/v1/ibft/titleFetch`
- `POST /transactions/v1/ibft/advice`
- `POST /transactions/v1/billInquiry`
- `POST /transactions/v1/billPayment`
- `POST /transactions/v1/initiateLocalFT`
- `POST /transactions/v1/fundsTransferLocal`
- `POST /transactions/v1/internalcashin`
- `POST /transactions/v1/internalcashout`
- `POST /agentapp/v1/customer/cashInInquiry`
- `POST /thirdparties/genrateOtp`

## Not in this first cut
- Debit card APIs (`cardLov`, `newCardRequest`, …)
- Agent KYC multipart
- Persistent DB (in-memory agents; restart clears new registrations except seed)

## Quick login test

```powershell
Invoke-RestMethod http://localhost:8092/agentapp/v1/login -Method POST -ContentType application/json -Body @{
  channel='AGNT'; imieNo='test-imei'; payload=@{
    accountRegTypeCode='C'; accountTypeCode='10'; fireBaseToken=''; password='1234'; username='03001234567'
  }
} | ConvertTo-Json -Depth 6
```
