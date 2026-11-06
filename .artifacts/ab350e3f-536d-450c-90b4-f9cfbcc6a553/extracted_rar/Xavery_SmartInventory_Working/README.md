# Xavery SmartInventory — runnable starter system

A responsive, browser-runnable prototype based on the uploaded capstone documentation for motorcycle-parts inventory monitoring.

## What works now
- Firebase Email/Password sign-in with self-registration for Staff
- Role-specific workspaces: Staff have Time Clock; Sales Staff have Point of Sale and Time Clock; Admin and Project Admin have the management workspace, including Price Management and Analytics
- Staff time clock with employee time-in/time-out records and an attendance history
- Dashboard summary, inventory valuation and low-stock warnings
- Product create/read/update/delete (administrator)
- Administrator-only product price management
- Admin sticker-label workspace for generating and printing item labels
- POS sales analytics based on completed sale movements
- Stock-in and stock-out movements with quantity validation and audit trail
- Camera permission workflow and image upload
- Optional YOLOv8 API integration endpoint
- CSV export for inventory and stock movements
- Firestore storage with live updates across signed-in dashboard clients
- Responsive layout for desktop and mobile browsers

## Run the web application
Camera APIs work on `localhost` or HTTPS. Do not just double-click `index.html` if you want to use the camera.

### Option A: Python
From this folder run:
```bash
python -m http.server 8080
```
Open `http://localhost:8080` in Chrome.

### Option B: VS Code
Install Live Server, right-click `index.html`, and choose **Open with Live Server**.

## Sign-in setup
The Android app and web app use the same Firebase Authentication project and Cloud Firestore collections for profiles, products, and stock movements. Both clients listen for live updates: product changes refresh the other client's inventory, dashboard, and stock alerts, movement changes refresh web reports and sales analytics, and role changes refresh the web workspace. Android scanner sales are written as priced POS movements so they are included in web analytics. Web registration sends a Firebase email verification link and requires verification before sign-in. If the account is not verified, signing in sends a fresh verification link. Public registration always creates `Staff` accounts; an Admin or Project Admin can change an account between `Staff` and `Sales Staff`, while only a Project Admin can assign Admin access. The roles are `Staff`, `Sales Staff`, `Admin`, and `Project Admin`. For the trusted account that should manage the system, set its Firestore `users/{UID}` role to `Project Admin` as described in [firebase/SETUP.md](firebase/SETUP.md). Older products stored only in Realtime Database are not automatically migrated. There are no built-in demo credentials.

## Run YOLOv8 object detection
The web interface calls `http://127.0.0.1:8000/detect`. The trained YOLOv8 model is included at `vision_api/best.pt`.

1. Install Python 3.10 or newer.
2. Follow the setup in `vision_api/README.md`.
3. Start the API, then use the scanner in the Point of Sale page.

The model recognizes six motorcycle-part classes. Detection quality depends on lighting, camera angle, and similarity to training images; a human confirms the catalog match and quantity before inventory changes.

In the Point of Sale workspace, a confirmed scan adds the matched item to the cart. Inventory is only reduced when the sale is completed.

## Firebase web dashboard
The web dashboard and Android client use Firebase Authentication and the same Cloud Firestore collections for profiles, products, and stock movements. Follow [firebase/SETUP.md](firebase/SETUP.md) to create the first administrator profile and publish the Firestore rules.

Existing browser-local demo data and Android Realtime Database inventory are not automatically copied to Firestore.

`Staff` accounts can access **Time Clock**. **Sales Staff** accounts can access both **Point of Sale** and **Time Clock**; employees can record one time-in and one time-out per local calendar day and view only their own attendance records. New registrations default to `Staff`; Admin and Project Admin can change Staff and Sales Staff roles, while only Project Admin can assign Admin access. Product price and catalog changes remain administrator-only. `Admin` can manage prices from **Price Management** and view completed-sale trends from **Analytics**. `Project Admin` has the same management workspace and is the only role allowed to assign Admin access. Publish the updated `firebase/firestore.rules` after changes.

## Important project scope notes
This package includes a web dashboard, not a native Android application. Your capstone documentation specifies Android Studio/Java, OpenCV Android SDK, YOLOv8, Firebase Authentication/Firestore, and a web admin panel. A native Android client and a trained model require Android build configuration, device testing, and your model/dataset. The included web app supports the inventory workflow while those components are integrated.
