# Firebase setup

The web dashboard and Android app use the same Firebase project, Firebase Authentication (Email/Password), and Cloud Firestore collections: `users`, `products`, `movements`, `attendance`, and `invites`. Both clients subscribe to live Firestore snapshots for shared records. Updates to products and stock movements made by either client therefore appear in the other client's dashboard, inventory, alerts, reports, and analytics as soon as Firestore delivers the update.

The Android app's dashboard, inventory, and alerts are driven by live `products` listeners. The web dashboard and analytics subscribe to live `products` and `movements` listeners. Android registration writes Staff profiles to Firestore; the web supports `Staff`, `Sales Staff`, and legacy `Inventory Clerk` role values. The web also checks Realtime Database as a fallback for older Android profiles and mirrors those profiles into Firestore.

Publish `firebase/firestore.rules` after changes. New profiles can only be created as `Staff`; only a `Project Admin` can change another user's role to `Staff`, `Admin`, or `Project Admin`. The rules recognize legacy `Administrator` and `Project Administrator` profiles during migration, but no new updates can assign those old values.

## Bootstrap the Project Administrator

Public registration always creates a `Staff` profile. Firestore rules prevent users from assigning themselves a role, so only trusted capstone team accounts should be granted the initial `Project Admin` role through Firebase Console.

1. Open `firebase/firestore.rules`, copy its contents into the Firebase Console **Firestore Database → Rules** tab, and click **Publish**. This allows registration to create Staff profiles while reserving role changes for trusted administrators.
2. Use the already-registered Android account; do not register it again. In Firebase Console, open **Authentication → Users**, find the account by email, and copy its UID.
3. Open **Firestore Database → Data → users**. Create a document whose ID is exactly that UID. Add string fields `name` and `email` with the account's actual details, and a string field `role` with the exact value `Project Admin`. If that UID document already exists, change only its `role` field.
4. Sign in to the web app with the same email and password used in Android. The Firestore profile takes precedence over the Android Realtime Database profile. The web session listens for role changes and refreshes access automatically.
5. To bootstrap additional trusted capstone team accounts, repeat these steps for each account.

Do not add administrator role selectors to public registration. Firebase Console edits bypass client Firestore rules and are the trusted bootstrap path for the first `Project Admin`.

## Register staff and assign a Company Administrator

Anyone can register through either client. Both clients create a `Staff` profile in Firestore; web accounts must verify their email before signing in. If a Staff member tries to sign in before verifying, the web app sends a fresh verification link. The roles are `Staff`, `Sales Staff`, `Admin`, and `Project Admin`. Admins and Project Admins can change users between `Staff` and `Sales Staff`; only a Project Admin can assign Admin access. A `Project Admin` can change roles for any account in the Android team screen, including other Project Admins and their own account. The web listens to each signed-in user's profile and refreshes the workspace when that role changes. Changing your own role away from `Project Admin` removes your role-management permissions; another Project Admin or Firebase Console access is then needed to restore them. Firestore rules enforce these restrictions independently of the interfaces. Existing profiles with legacy `Inventory Clerk`, `Administrator`, or `Project Administrator` values remain recognized until migrated to the canonical values.

Web workspace access follows the profile role: `Staff` accounts see Time Clock, while `Sales Staff` accounts see both Time Clock and Point of Sale and can update inventory quantities when completing sales. Employees can clock in and out once per local calendar day and can read only their own attendance records. New registrations default to `Staff`; Admin and Project Admin can assign either `Staff` or `Sales Staff`, while only Project Admin can assign Admin access. The rules permit employees to check whether an attendance document exists before their first time-in; reads of existing records are still restricted to their owner. `Admin` and `Project Admin` users retain the management workspace, including Price Management and Analytics. Sales Staff cannot change product prices or other catalog details.

Analytics are calculated from completed Point of Sale movements. New POS movements record the unit price and sale total at checkout so future price changes do not rewrite historical revenue.

## Notes

- The web Firebase config in `firebase-config.js` and Android `google-services.json` are public client configuration, not service-account keys.
- Firestore data is not seeded automatically. Add products in the dashboard after the first administrator signs in.
- Existing browser-local demo data and legacy products stored only in Realtime Database are not automatically copied to Firestore.
- Rules permit inventory clerks to adjust only product quantities; administrator-only catalog fields remain protected.
- Deploy the rules and test with both roles before using real inventory data.
- Older account profiles stored only in Realtime Database can be mirrored into Firestore when the user first signs in on the web. Legacy products stored only in Realtime Database are not copied automatically; migrate them into Firestore before relying on cross-client inventory sync.
