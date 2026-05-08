# IDVerse Bruno Collection

API collection for the IDVerse verification service. Covers the local app endpoints and the upstream IDVerse API.

## Prerequisites

- Bruno desktop app installed
- App running: `mvn spring-boot:run` (or `docker-compose up -d` for Docker)

## Setup

### 1. Open the collection

Open Bruno → **Open Collection** → select this directory (`idverse-api/idverse/`).

### 2. Fill in environment variables

Click the **Environments** dropdown (top-right) → select your environment → click the pencil icon.

Fill in the secret values (marked with a lock icon):

| Variable | Where to get it |
|---|---|
| `AUTH_KEY` | From `AUTH_KEY` in the app's `.env` file |
| `IDVERSE_CLIENT_ID` | From `IDVERSE_CLIENT_ID` in the app's `.env` |
| `IDVERSE_CLIENT_SECRET` | From `IDVERSE_CLIENT_SECRET` in the app's `.env` |
| `WEBHOOK_JWT_TOKEN` | See note below |
| `IDVERSE_ACCESS_TOKEN` | Run **upstream-idverse / Get OAuth Token** first, copy `access_token` |
| `JWT_SECRET_KEY` | From `JWT_SECRET_KEY` in the app's `.env` |

#### Getting `WEBHOOK_JWT_TOKEN`

The app auto-generates JWT tokens when calling the IDVerse API. To capture one:

1. Enable `VERBOSE=SECRET` in the app's `.env` and restart
2. Run **verification / Send Verification** (or dry-run)
3. In the server console, find the line `notifyUrlCompleteAuthKey: Bearer <token>`
4. Copy everything after `Bearer ` and paste it into `WEBHOOK_JWT_TOKEN`

Alternatively, if you know `JWT_SECRET_KEY`, you can generate a token locally:
```bash
# Python one-liner
python3 -c "
import jwt, datetime
key = 'YOUR_JWT_SECRET_KEY'
token = jwt.encode({'sub':'webhook-complete','iat':datetime.datetime.utcnow(),'exp':datetime.datetime.utcnow()+datetime.timedelta(hours=24)}, key, algorithm='HS256')
print(token)"
```

---

## Happy Path Walkthrough

### Step 1 — Confirm the app is healthy

Run **oauth-test / Test OAuth**. Expect `"status": "SUCCESS"`. If it fails, check `IDVERSE_CLIENT_ID` and `IDVERSE_CLIENT_SECRET`.

### Step 2 — Authenticate

Run **auth / Get Auth Token**. Bruno follows the redirect to the web UI root. A `302` with a `Location` header containing `jwt_key` means auth succeeded.

### Step 3 — Dry-run verification

Run **verification / Verify Test (Dry Run)** (`dryRun=true`). No SMS is sent; a mock record is saved to the database. Expect:
```json
{ "dryRun": "true", "status": "success", "transactionId": "txn-test-001" }
```

### Step 4 — Check status

Run **status / Status by Transaction ID** with the `transactionId` from Step 3. Expect the record with status `SMS SENT`.

### Step 5 — Simulate a webhook event

Run **webhook / Simulate Webhook (Event)** — simulates IDVerse calling back with `"event": "pending"`. Requires `WEBHOOK_JWT_TOKEN` to be set.

### Step 6 — Simulate completion

Run **webhook / Simulate Webhook (Complete)** with `"event": "completedPass"`. Check **status / Status by Transaction ID** again to see the updated record.

---

## Request Reference

### auth/
| Request | Method | Endpoint | Auth |
|---|---|---|---|
| Get Auth Token | GET | `/api/getAuth?auth_key={{AUTH_KEY}}` | query param |

### verification/
| Request | Method | Endpoint | Auth |
|---|---|---|---|
| Send Verification | POST | `/api/verify` | none |
| Verify Test (Dry Run) | POST | `/api/verify/test?dryRun=true` | none |
| Verify Test (Real Call) | POST | `/api/verify/test?dryRun=false` | none |
| Get All Verifications | GET | `/api/verifications` | none |
| Get Verification by ID | GET | `/api/verifications/{id}` | none |

### status/
| Request | Method | Endpoint | Auth |
|---|---|---|---|
| Status by Reference ID | GET | `/api/status/reference/{referenceId}` | none |
| Status by Transaction ID | GET | `/api/status/transaction/{transactionId}` | none |

### webhook/
| Request | Method | Endpoint | Auth |
|---|---|---|---|
| Simulate Webhook (Complete) | POST | `/api/webhook` | Bearer JWT |
| Simulate Webhook (Event) | POST | `/api/webhook` | Bearer JWT |
| Update Status (No Auth) | POST | `/api/updateStatus` | none |

Valid event values: `pending`, `termsAndConditions`, `idSelection`, `personalDetails`, `liveness`, `expired`, `cancelled`, `completedPass`, `completedFlagged`

### oauth-test/
| Request | Method | Endpoint | Auth |
|---|---|---|---|
| Test OAuth | GET | `/test/oauth` | none |
| Test OAuth (Verbose) | GET | `/test/oauth?verbose=debug` | none |
| Clear OAuth Cache | POST | `/test/oauth/clear` | none |
| Test Config | GET | `/test/config` | none |

### mock/
| Request | Method | Endpoint | Auth |
|---|---|---|---|
| Mock OAuth Token | POST | `/api/3.5/oauthToken` | none |

### upstream-idverse/
| Request | Method | Endpoint | Auth |
|---|---|---|---|
| Get OAuth Token | POST | `{{IDVERSE_OAUTH_URL}}` | form credentials |
| Send SMS Verification | POST | `{{IDVERSE_API_URL}}` | Bearer `{{IDVERSE_ACCESS_TOKEN}}` |

Run **Get OAuth Token** first, then copy the `access_token` value into `IDVERSE_ACCESS_TOKEN` in your environment before running **Send SMS Verification**.
