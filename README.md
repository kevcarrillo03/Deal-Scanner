# Deal Scanner

A mobile app that finds the best price for a product by scanning its barcode. Point your phone's camera at a UPC barcode, and Deal Scanner identifies the product and compares its current price across major US retailers like Walmart, Target, and Walgreens, cheapest first.

The project has two parts: a **React Native (Expo) app** for scanning and showing results, and a **Spring Boot REST API** that looks up the product, gathers prices from two data sources, and caches the results.

## Features

- **Barcode scanning** of UPC-A, UPC-E, EAN-13, and EAN-8 barcodes with the phone camera, with an on-screen guide box.
- **Product lookup** that turns a barcode into a product name through UPCItemDB, falling back to Open Food Facts when UPCItemDB is rate-limited, unavailable, or doesn't know the product.
- **Price comparison** that merges two data sources:
  - Google Shopping results through SerpApi.
  - Store prices recorded by UPCItemDB, ignoring any older than 90 days.
- **Clean results**: store names are normalized ("Wal-Mart.com" and "Walmart - Seller" both become "Walmart"), only the cheapest listing per store is kept, and results are sorted cheapest first.
- **Caching** of results for 12 hours, in memory and in PostgreSQL, so repeat scans return in milliseconds, survive restarts, and don't use API quota.
- **Scan and price history**: a Recent Scans screen for each phone, and every price check saved over time, shown on the results screen with the lowest price seen.
- **Clear error states** for unknown barcodes, rate limits, and network problems, plus a "Prices as of" time so cached prices are never mistaken for live ones.

## Tech Stack

| Part | Technologies |
|---|---|
| Mobile app | React Native 0.86, Expo SDK 57, Expo Router, expo-camera, TypeScript |
| Backend | Java 17, Spring Boot 4, Spring WebClient (non-blocking HTTP), Project Reactor, Caffeine cache |
| Database | PostgreSQL 18, Spring Data JPA, Flyway migrations, Docker Compose |
| External APIs | [UPCItemDB](https://www.upcitemdb.com/) and [Open Food Facts](https://world.openfoodfacts.org/) (product lookup), [SerpApi](https://serpapi.com/) Google Shopping (prices) |

## How It Works

```mermaid
sequenceDiagram
    participant App as Mobile App
    participant API as Spring Boot API
    participant Cache as Caffeine Cache
    participant UPC as UPCItemDB
    participant OFF as Open Food Facts
    participant Serp as SerpApi (Google Shopping)

    App->>API: GET /api/v1/scan/{upc}
    API->>Cache: Look up UPC
    alt Cached (within 12 hours)
        Cache-->>API: Stored result
    else Not cached
        API->>UPC: Look up barcode
        alt Found
            UPC-->>API: Product name + recorded store prices
        else Rate-limited, unavailable, or not found
            API->>OFF: Look up barcode
            OFF-->>API: Brand, product name, size, image
        end
        API->>Serp: Search Google Shopping for product name
        Serp-->>API: Shopping listings
        API->>API: Match retailers, drop stale prices,<br/>keep cheapest per store, sort
        API->>Cache: Store result
    end
    API-->>App: JSON: product + prices
```

1. The app scans a barcode and opens the results screen, which calls the backend.
2. The backend checks its cache and database. On a miss, it asks UPCItemDB what the product is. UPCItemDB also returns store prices it has recorded. If UPCItemDB is rate-limited, unavailable, or doesn't know the barcode, the backend asks Open Food Facts instead, and then falls back to the product saved from an earlier scan.
3. The backend searches Google Shopping (through SerpApi) for the product name.
4. Listings from both sources are matched against a list of major retailers, and stale or invalid prices are dropped. The cheapest listing per store is kept.
5. The result is cached and returned to the app, which shows each store's price with a link to the listing.

## Design Decisions

- **Two price sources instead of one.** Google Shopping's default results are often dominated by small shops and miss the big retailers. UPCItemDB's recorded prices fill that gap for free, since they come with the product lookup. Its prices can be years old, so anything older than 90 days is dropped.
- **An async cache that shares in-flight lookups.** The cache is a Caffeine `AsyncCache`, so if two scans of the same barcode arrive at the same time they share one lookup instead of making duplicate API calls. Failed lookups are not cached, so a temporary error doesn't stick for 12 hours.
- **A fallback for product lookup.** UPCItemDB's free trial limits requests per IP address, and hosting platforms like Render share outgoing IP addresses between many apps, so the shared address is often already over the limit. The backend tries UPCItemDB, then Open Food Facts (free, no key, and strong coverage of groceries), then the product saved from an earlier scan, so scanning keeps working when one source is unavailable.
- **Degrading gracefully.** If the Google Shopping search fails, the app still gets the product name and UPCItemDB's prices instead of an error. If the database is unreachable, scans still work without being saved.
- **Keeping secrets out of the code.** The SerpApi key is read from an environment variable or a git-ignored `.env` file, never committed.

## API

### `GET /api/v1/scan/{upc}`

Looks up a barcode and returns the product with its prices, cheapest first.

**Example response** (`200 OK`):

```json
{
  "upc": "028400183826",
  "product_name": "Lay's Baked Original Potato Crisps 6.25 Ounce Plastic Bag",
  "prices": [
    {
      "store": "Target",
      "price": 4.19,
      "title": "Lay's Oven Baked Original Potato Chips - 6.25oz",
      "link": "https://...",
      "thumbnail": "https://..."
    },
    {
      "store": "Walgreens",
      "price": 4.99,
      "title": "Lay's Chips Baked - 6.25 oz",
      "link": "https://...",
      "thumbnail": "https://..."
    }
  ],
  "fetched_at": "2026-09-28T20:32:42.838255Z"
}
```

**Errors:**

| Status | Meaning |
|---|---|
| `404 Not Found` | No source (UPCItemDB, Open Food Facts, or the database) knows this barcode |
| `429 Too Many Requests` | UPCItemDB's rate limit was reached and no other source could identify the product |
| `502 Bad Gateway` | UPCItemDB failed and no other source could identify the product |

An optional `X-Device-Id` header tags the scan with the phone that made it, for the recent scans list.

### `GET /api/v1/scans/recent`

The products a phone scanned most recently (up to 20), each listed once with the best price from its latest price check. Requires the `X-Device-Id` header (`400` if missing).

```json
[
  {
    "upc": "028400183826",
    "product_name": "Lay's Baked Original Potato Crisps 6.25 Ounce Plastic Bag",
    "image_url": "https://...",
    "last_scanned_at": "2026-09-28T21:09:40.193636Z",
    "best_price": { "store": "Target", "price": 4.19 }
  }
]
```

### `GET /api/v1/products/{upc}/history?days=30`

A product's price checks over the last `days` days (1 to 365, default 30), newest first, with the lowest price seen. Returns `404` if the product has never been scanned.

```json
{
  "upc": "028400183826",
  "product_name": "Lay's Baked Original Potato Crisps 6.25 Ounce Plastic Bag",
  "days": 30,
  "lowest_price": { "store": "Target", "price": 4.19, "checked_at": "2026-09-28T21:09:32.741747Z" },
  "checks": [
    {
      "checked_at": "2026-09-28T21:09:32.741747Z",
      "prices": [
        { "store": "Target", "price": 4.19 },
        { "store": "Walgreens", "price": 4.99 }
      ]
    }
  ]
}
```

## Getting Started

### Prerequisites

- Java 17 or newer
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (runs the PostgreSQL database)
- Node.js (LTS)
- The [Expo Go](https://expo.dev/go) app on your phone
- A free [SerpApi](https://serpapi.com/) API key (UPCItemDB's trial endpoint and Open Food Facts need no key)

### 1. Run the backend

```bash
cd backend
```

Create a file named `.env` in the `backend` folder with your SerpApi key (no quotes, no spaces):

```
SERPAPI_API_KEY=your_serpapi_key
```

Then start the server (with Docker running):

```bash
./mvnw spring-boot:run
```

This also starts the PostgreSQL database from `compose.yaml` automatically, on port `5433`. The API runs on port `8080`. Without a key, the backend still works but only returns UPCItemDB's prices.

### 2. Run the mobile app

Your phone must be on the same Wi-Fi network as your computer. Find your computer's local IP address (on macOS: `ipconfig getifaddr en0`), then create `frontend/.env.local`:

```
EXPO_PUBLIC_API_URL=http://YOUR_COMPUTER_IP:8080
```

Install dependencies and start Expo:

```bash
cd frontend
npm install
npx expo start
```

Scan the QR code shown in the terminal with your phone's camera (iOS) or the Expo Go app (Android).

## Deploying (free)

The backend deploys as a Docker container to [Render](https://render.com)'s free plan, with the database on [Neon](https://neon.tech)'s free PostgreSQL. Free Render services sleep after about 15 minutes without traffic, so the first request after that is slow while the server wakes up, a minute or more on the free plan's small CPU. The app shows a "Waking up the server" message during that wait.

### 1. Create the database on Neon

1. Create a free Neon project.
2. Copy its connection details. Neon shows a URL like `postgresql://USER:PASSWORD@HOST/DATABASE?sslmode=require`. Spring needs it split into three values:
   - `SPRING_DATASOURCE_URL` = `jdbc:postgresql://HOST/DATABASE?sslmode=require`
   - `SPRING_DATASOURCE_USERNAME` = `USER`
   - `SPRING_DATASOURCE_PASSWORD` = `PASSWORD`

The tables are created automatically by Flyway on the backend's first start.

### 2. Deploy the backend on Render

1. Push this repo to GitHub.
2. In Render, choose **New → Blueprint** and select the repo. Render reads [`render.yaml`](render.yaml) and creates the service from `backend/Dockerfile`.
3. Enter the environment variables it asks for: `SERPAPI_API_KEY` and the three `SPRING_DATASOURCE_*` values from Neon.
4. Wait for the deploy to finish, then open `https://YOUR-SERVICE.onrender.com/actuator/health`. It should show `{"status":"UP"}`.

### 3. Point the app at the deployed backend

Set the backend address in `frontend/.env.local` and restart Expo with `npx expo start -c`:

```
EXPO_PUBLIC_API_URL=https://YOUR-SERVICE.onrender.com
```

The app now works on any network, not just your Wi-Fi.

### Running the Docker image locally

```bash
cd backend
docker build -t deal-scanner-backend .
docker run -p 8080:8080 \
  -e SERPAPI_API_KEY=your_key \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5433/dealscanner \
  -e SPRING_DATASOURCE_USERNAME=dealscanner \
  -e SPRING_DATASOURCE_PASSWORD=dealscanner \
  deal-scanner-backend
```

This uses the database from `compose.yaml` (`docker compose up -d`).

## Project Structure

```
Deal-Scanner/
├── render.yaml                               Render deployment blueprint
├── backend/                                  Spring Boot REST API
│   ├── Dockerfile                            Production container image
│   ├── compose.yaml                          Local PostgreSQL database
│   └── src/main/
│       ├── resources/db/migration/           Flyway SQL migrations
│       └── java/com/kevincarrillo/dealscanner/
│           ├── ScanController.java           /api/v1/scan endpoint
│           ├── ScanService.java              Scan flow: cache, database, external APIs
│           ├── UpcItemDbClient.java          Product lookup on UPCItemDB
│           ├── OpenFoodFactsClient.java      Fallback product lookup on Open Food Facts
│           ├── ScanStore.java                Saving and loading scans in the database
│           ├── PriceService.java             Price search, retailer matching, merging
│           ├── HistoryController.java        Recent scans and price history endpoints
│           ├── HistoryService.java           Builds the history responses
│           ├── db/                           JPA entities and repositories
│           └── *Response.java, ScanResult.java, StorePrice.java  JSON models
└── frontend/                                 React Native (Expo) app
    └── src/
        ├── app/
        │   ├── _layout.tsx                   Navigation stack
        │   ├── index.tsx                     Camera scanner screen
        │   ├── history.tsx                   Recent scans screen
        │   └── results/[upc].tsx             Price results and price history
        ├── api/
        │   ├── scan.ts                       Backend client and shared types
        │   └── deviceId.ts                   Random per-phone ID for scan history
        ├── hooks/useIsSlow.ts                Detects slow loads (server waking up)
        └── utils/format.ts                   Price and date formatting
```

## Limitations

- **US only.** Product coverage, retailers, and prices (USD) are all US-focused.
- **Free-tier API limits.** UPCItemDB's trial allows about 100 lookups per day per IP address, and SerpApi's free plan allows 250 searches per month. Caching reduces how quickly these are used.
- **Non-food products on shared hosting.** When UPCItemDB is rate-limited (common on Render, where the IP address is shared), products are identified through Open Food Facts, which covers food and drinks. Other products can't be identified until UPCItemDB is available, and prices then come from Google Shopping only.
- **Cold starts on the free host.** The deployed backend sleeps when idle, so the first request after a quiet period can take a minute or more.
