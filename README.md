# HUNTER label review prototype

HUNTER is a reviewer-led grocery-label pre-check. This project implements the
planned React/Vite interface and Spring Boot API. It does **not** certify a
product, give a legal opinion, or claim that any checklist prompt is a verified
FSSAI requirement.

## Run locally

Requirements: Node.js 20.19+ and Java 21.

In one terminal, start the API:

```powershell
cd backend
mvn spring-boot:run
```

In a second terminal, start the frontend:

```powershell
cd frontend
npm install
npm run dev
```

Open the Vite URL printed in the terminal (normally `http://127.0.0.1:5173`).
Vite proxies `/api` requests to the Spring Boot service at `127.0.0.1:8080`.

## Implemented workflow

- Scan a product barcode with a supported device camera or enter it manually. On explicit lookup, only the barcode is sent to Open Food Facts; its community-contributed product data must be checked against the package.
- Use **Scan product** to capture a PNG or JPEG package-label photo (maximum 10 MB) with the device camera, or drag an image onto the drop area. Camera capture depends on browser/device support and permission, and deployed sites should use HTTPS.
- Confirm or correct the imported/OCR label text, then select the MVP packaged-snacks demo category.
- Optionally provide brand-approved product facts to compare with claim phrases.
- Run deterministic, versioned text-cue prompts.
- Review the image, label text, findings and their checklist version.
- Record a reviewer decision and optional note; review history is persisted.
- Find stored reviews and download or remove a review through the API.
- Persist demo users, versioned checklist prompts, review records, findings and
  reviewer decision history in a relational H2 database.
- Save uploaded image files with random storage names outside the Vite public
  folder. Uploads are restricted to readable PNG/JPEG images with safe size and
  dimension limits.

Local OCR uses the free Tesseract command-line tool and does not upload package
images. If Tesseract is not installed, manual text entry remains available.
Barcode lookup sends the barcode only to Open Food Facts when the reviewer asks
for product details. Optional AI reviewer suggestions use Azure OpenAI and
remain unavailable until separately configured.

## Local OCR setup

Install Tesseract OCR on Windows (for example, from an elevated or regular
PowerShell window, depending on your device policy):

```powershell
winget install --id UB-Mannheim.TesseractOCR --exact
```

Restart the Spring Boot API after installation. By default, HUNTER runs
`tesseract` from `PATH` with the English language data. If it is installed in a
non-standard location, set the executable path before starting the API:

```powershell
$env:HUNTER_TESSERACT_PATH = "C:\Program Files\Tesseract-OCR\tesseract.exe"
# Optional language packs, e.g. English and Hindi:
$env:HUNTER_TESSERACT_LANGUAGES = "eng+hin"
```

Local OCR processes the selected image on this computer only when the reviewer
clicks **Extract text locally**. OCR output can be inaccurate and must be
corrected against the image. No OCR image is sent to Azure.

## Optional Azure OpenAI suggestions

Azure OpenAI reviewer-note suggestions are optional and **not configured by
default**. The feature sends label text, supplied product facts, and review
findings to Azure only after the reviewer separately consents and requests
suggestions. It cannot certify compliance; all generated wording must be
reviewed.

Configure credentials in the local environment before starting the API; do not
commit them to source control:

```powershell
$env:HUNTER_AZURE_OPENAI_ENDPOINT = "https://<openai-resource>.openai.azure.com"
$env:HUNTER_AZURE_OPENAI_KEY = "<resource-key>"
$env:HUNTER_AZURE_OPENAI_DEPLOYMENT = "<chat-deployment-name>"
```

The Azure OpenAI deployment must be a chat-completions model deployed in your
resource. Resource availability and API versions can differ by region; verify
the deployed model, endpoint, API version, retention, and data-processing terms
before enabling the service. Capabilities disclose whether each provider is
configured. A configured provider is still called only after an explicit
reviewer action.

Official source references (checked 5 Nov 2026):

- [FSSAI Labelling and Display Regulations compendium (9 Sep 2025)](https://www.fssai.gov.in/docs/food-law/regulations/Comp_Labelling%20Display_Version%20VIII_09_09_2025.pdf)
- [FSSAI Labelling and Display amendments](https://www.fssai.gov.in/food-law/regulations/amendments/labelling-display)
- [FSSAI regulations portal](https://www.fssai.gov.in/food-law/regulations)
- [Open Food Facts](https://world.openfoodfacts.org/) (community-contributed product data)
- [Tesseract OCR](https://github.com/tesseract-ocr/tesseract)
- [Azure OpenAI chat completions REST API](https://learn.microsoft.com/en-us/rest/api/microsoft-foundry/azureopenai/chat)

The sample checklist remains `UNVERIFIED_DEMO`: the project team must assess
the current regulation text and subsequent amendments, select provisions
relevant to the intended food category, and approve/version them before using
the software for a regulatory review.

The dashboard's generic produce photograph is sourced from
[Unsplash](https://images.unsplash.com/photo-1542838132-92c53300491e).

## Source and service policy

The initial `demo-v0.1` checklist is made of example prompts only and is marked
`UNVERIFIED_DEMO`; it does not perform an official FSSAI compliance check. The
team must select and review current official FSSAI material for the chosen
category before using regulatory checks. Open Food Facts is a community
database, not an authoritative label source. Tesseract is the local OCR engine.
Azure OpenAI is used only for optional reviewer suggestions. None of these
sources or services is accuracy-verified. Run OCR and product lookup checks
against representative, permitted samples before use.

## Development-only security boundary

The API binds to `127.0.0.1`; CORS is restricted to the local Vite origin. This
prototype has no authentication or per-user authorization. Do not bind it to a
public interface or expose it to a network. Before deployment, add authenticated
identity, review ownership and role checks, CSRF/transport protections,
retention/deletion policies, production database migrations, and secured file
storage.

Local database files and uploaded images are written under `backend/data/`,
which is ignored by Git. Deleting a review through the API removes its image
and database record.

## Validation

```powershell
cd frontend
npm run build
cd ..
mvn -f backend/pom.xml test
```
