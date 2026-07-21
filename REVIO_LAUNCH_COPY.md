# Revio — Privacy, Safety & Store-Submission Copy

**Status:** pre-launch draft.  
**Owner:** Revio Team  
**Support:** threvioapp@gmail.com

This document is the source copy for Revio's MVP. Replace every item marked `[REQUIRED BEFORE LAUNCH]` before publishing the policy or submitting the app. It reflects the current MVP: public car-spotting posts, comments, likes, optional post-time location, Google/Firebase sign-in, Firebase Analytics, and image storage in Cloudflare R2. Friends are not part of the MVP.

---

## 1. Required pre-launch product work

### Release blockers

- [ ] **Implement “Block user” before release.** This is required for a public user-generated-content feed. A blocked person's posts and comments must no longer be shown to the blocking user. The user should be able to block from a profile, post menu, and comment menu. Blocked users must not be told who blocked them. **Status: not started — no `Block`/`BlockedUser` code exists in either repo yet.**
- [ ] Allow reports for **posts, comments, and users**, not only posts. **Status: posts only today.** Server implements `POST /api/posts/{postId}/reports` (`features/report/ReportRoutes.kt`), and `ReportReason` only contains post-related reasons. Comment and user reporting still need routes, DTOs, and reasons.
- [ ] Add a moderation workflow: review reports, remove prohibited content, warn/suspend/remove repeat offenders, and keep an internal audit trail. **Status: not started.** `ReportStatus` (PENDING/REVIEWED/DISMISSED) exists on the model but nothing ever transitions a report out of `PENDING` (`features/report/ReportDAO.kt`); there are no admin/moderation routes, no suspend/ban mechanism, and no audit trail.
- [ ] Require acceptance of the Terms of Use and Community Guidelines before a user creates an account or uploads content. **Status: not started.** `RegisterRequest` has no `termsAccepted` field on either server or Android; Settings only links to a static Terms screen.
- [ ] Implement account deletion in the app, in Account Settings. It must call the existing account-deletion backend endpoint and delete the account, profile, posts, comments, likes, reports, sessions, and stored images. **Status: backend and repository/API client exist, but there is no UI entry point.** Server `DELETE /auth/account` (`features/auth/AuthRoutes.kt`) correctly cascades account/profile/posts/comments/likes/reports via FK `CASCADE`. Android has `AuthApi.deleteAccount()` and `AuthRepositoryImpl.deleteAccount()`, but no screen or button in Settings calls it yet.
- [ ] Publish a public web page that lets users request account deletion. Google Play requires this in addition to in-app account deletion.
- [ ] Verify that account deletion also deletes image objects in Cloudflare R2; database cascade deletion alone does not delete an object in object storage. **Status: not implemented.** `AuthService.deleteCredentials` performs the DB cascade only and never calls `StorageService`/`deleteImage`, so R2 objects for a deleted account are orphaned.
- [ ] Add a Child Safety Standards page and a child-safety contact method before submitting in Google Play's Social category.
- [ ] Implement an age gate (date of birth capture + 16+ enforcement) before submitting. **Status: not implemented.** No `dateOfBirth`/`age` field exists anywhere in either repo, even though §2.A of the Privacy Policy below states date of birth is collected and used to apply the age requirement.
- [ ] Strip EXIF metadata (including GPS tags) from uploaded photos before storage, as §2.C of the Privacy Policy claims. **Status: not implemented — and currently does the opposite of "stripped."** `core/image/ImageCompressor.kt` reads EXIF orientation and bakes the rotation into the output bitmap, but never clears/strips EXIF tags before upload.
- [ ] Integrate Firebase Analytics (or remove all analytics wording from this document) before submitting. **Status: not implemented.** No `FirebaseAnalytics` reference exists anywhere in `revio-android`. §2.D of the Privacy Policy and the "Help improve Revio" consent copy in §5 currently describe a feature that does not exist in the app.

### Accuracy blockers for the policy

- [ ] Add the legal name and country/address of the data controller: `[REQUIRED BEFORE LAUNCH: legal entity or individual name and postal address]`.
- [ ] Add the production API/database host and its processing region after it is chosen.
- [ ] Verify the final authentication implementation. **Confirmed: the current Android code uses Google Sign-In (`GoogleSignIn`/`GoogleSignInOptions` in `AuthScreen.kt`) with server-side token verification — there is no Firebase Authentication anywhere in the app.** The Firebase Auth wording in §2.A and §5 of the Privacy Policy below is therefore inaccurate today and must be replaced with Google Sign-In wording (or the Firebase wording restored only once Firebase Authentication actually ships).
- [ ] Decide whether Firebase Analytics begins only after consent where required. Do not say that analytics is consent-based unless the app enforces it. **Confirmed: Firebase Analytics is not integrated in the app at all today** — §2.D of the Privacy Policy and the "Help improve Revio" consent screen in §5 describe a feature that does not exist. Either integrate the SDK (with consent gating if required) or remove all analytics wording before launch.
- [ ] Do not collect a phone number until a real MVP feature requires it. A future use is not a valid reason to collect data now. **Confirmed: phone number is already collected end-to-end** (server migrations/DTOs, Android `PersonalInfoScreen`), but the Privacy Policy §2.A below does not mention it. Either remove phone collection before launch, or add "phone number" to the list of account/profile data collected in §2.A and confirm the MVP need for it.
- [ ] Confirm the minimum age gate: **16+ is the recommended MVP policy**. **Confirmed: not implemented.** No date-of-birth capture or age check exists in either repo, even though §2.A of the Privacy Policy states date of birth is collected to apply the age requirement, and §1 of the Terms states users must be 16+.

---

## 2. Privacy Policy

**Effective date:** [REQUIRED BEFORE LAUNCH: DD Month YYYY]

### Privacy Policy for Revio

Revio is a car-spotting community where users can create profiles, share car photos and posts, discover public posts, and interact through comments and likes. This Privacy Policy explains how Revio Team (“Revio”, “we”, “us”, or “our”) collects, uses, shares, stores, and protects personal data when you use the Revio mobile application and related services (collectively, the “Services”).

**Data controller:** [REQUIRED BEFORE LAUNCH: legal entity/individual name, registered or business address, country].  
**Contact:** threvioapp@gmail.com

By creating an account or using the Services, you acknowledge the practices described in this Privacy Policy. If you do not agree, please do not use the Services.

### 1. Who may use Revio

Revio is intended for users aged **16 or older**. We do not knowingly allow people under 16 to create an account. If you believe that a person under 16 has provided us with personal data, please contact us at threvioapp@gmail.com so we can investigate and delete the account where appropriate.

### 2. Data we collect

#### A. Account and profile data

When you create or use an account, we may collect:

- email address;
- account identifier and authentication information;
- full name, username, date of birth, country, and profile photograph;
- account statistics, including SpotScore and streak information; and
- account and security information, such as login sessions, device identifier, device name, IP address, user-agent information, and timestamps.

Your date of birth is used to apply the age requirement. It is not displayed publicly. `[REQUIRED BEFORE LAUNCH: not yet implemented — no date-of-birth field or age check exists in either repo today. Implement the age gate or revise this paragraph before publishing.]` `[REQUIRED BEFORE LAUNCH: phone number is collected end-to-end in the app today but is not listed above — add it here, or remove phone collection before launch (see Accuracy blockers).]`

#### B. Content and social activity

We collect the content you choose to provide, including car photographs, post captions, car information, comments, likes, reports, and related timestamps. Posts, comments, likes, account statistics, and profile information are part of the social functionality of Revio and may be visible to other users as described below.

#### C. Location data

Revio requests location permission only when you choose to create a post. Location is optional: you can publish a post without granting location permission.

If you grant permission and choose to use the feature, we may collect and store the latitude and longitude connected with that post, and derive or store the associated town and country. Other users are shown no more than the town and country; Revio does not display your precise coordinates to other users. Revio does not collect location in the background.

Uploaded photographs are reprocessed on-device before upload: the image is re-encoded from pixel data, so EXIF metadata — including GPS location, timestamp, and device model — is not transmitted and never reaches storage. Correct visual orientation is preserved by applying rotation directly to the pixels.

#### D. Analytics and technical data

We use Firebase Analytics to understand how the Services are used, measure feature performance, and improve reliability and user experience. This may involve usage data, app interactions, device and app information, identifiers, and diagnostic information collected by Firebase/Google according to its applicable terms and privacy practices. `[REQUIRED BEFORE LAUNCH: not yet implemented — no Firebase Analytics SDK exists in revio-android today. Integrate it or remove this section and the analytics consent screen in §5 before publishing.]`

We do not use analytics data for targeted advertising and do not sell personal data.

### 3. How we use data

We use personal data to:

- create and secure accounts, authenticate users, and prevent abuse or fraud;
- operate profiles, posts, comments, likes, scores, streaks, and related social features;
- show the town and country associated with a post when location is provided;
- store, deliver, and display user-uploaded images;
- investigate reports, enforce our Terms of Use and Community Guidelines, and protect users and the Services;
- maintain, troubleshoot, analyse, and improve the Services; and
- comply with legal obligations and respond to lawful requests.

Where required by applicable law, we rely on your consent for optional data processing, such as permission-based location access and analytics where consent is required. We otherwise process data as necessary to provide the Services, protect the Services and users, or meet legal obligations.

### 4. What is public

The following information may be visible to other Revio users: your full name, username, profile photograph, country, SpotScore, streak, posts, comments, and likes. A post may show its town and country if you provided location permission for that post.

Do not include personal, confidential, or sensitive information in public posts or comments. Content you make public can be viewed, copied, or shared by other users.

### 5. How we share data

We share data only as needed to operate the Services, including with:

- **Cloudflare R2**, which stores user-uploaded images;
- **Google**, for Google Sign-In authentication (`[REQUIRED BEFORE LAUNCH: remove "Firebase Analytics" from this line unless the SDK is actually integrated before launch — see Accuracy blockers]`);
- **[REQUIRED BEFORE LAUNCH: API/database hosting provider and region]**, which hosts and processes the Revio backend and database;
- service providers that help us operate, secure, or support the Services, under appropriate contractual or legal safeguards; and
- authorities or other parties when required by law, necessary to protect rights and safety, investigate abuse, or enforce our policies.

We do not sell personal data and do not share personal data for cross-context behavioural advertising.

### 6. International transfers

Revio is available globally. Your data may be processed in countries other than the country where you live, including countries where our providers operate. We use appropriate safeguards required by applicable law for international data transfers.

### 7. Retention and account deletion

We retain personal data while your account remains active and only for as long as needed to provide the Services, resolve disputes, enforce our agreements, protect users, or meet legal obligations.

You can initiate account deletion in the app through **Settings → Account → Delete account**. You can also submit a deletion request at **[REQUIRED BEFORE LAUNCH: public account-deletion webpage URL]**.

When an account-deletion request is completed, we delete or irreversibly de-identify personal data associated with the account, including profile data, posts, comments, likes, reports, sessions, and Revio-controlled image objects, unless we must retain limited information to comply with law, resolve a dispute, or protect the security and integrity of the Services. De-identified, aggregated analytics that cannot reasonably identify you may be retained.

Deletion is permanent and cannot be undone. We will state the deletion timeline in the app and on the deletion webpage before launch.

### 8. Your privacy rights

Depending on where you live, you may have rights to request access to, correction of, deletion of, restriction of, or portability of your personal data, and to object to certain processing. You may also withdraw consent where we rely on consent.

To make a request, email threvioapp@gmail.com from the email address associated with your account. We may need to verify your identity before completing a request. You may also have the right to complain to your local data-protection authority.

### 9. Security

We use reasonable technical and organisational measures designed to protect data. No method of transmission or storage is completely secure, so we cannot guarantee absolute security.

### 10. Third-party services

Google and Firebase services are governed by their own privacy terms and policies. Cloudflare R2 processes stored images as a service provider for Revio. We are not responsible for the independent privacy practices of third parties outside the Services.

### 11. Changes to this Privacy Policy

We may update this Privacy Policy when our Services or legal obligations change. If we make material changes, we will provide notice in the app or by another appropriate method. The “Effective date” above shows when this Policy was last updated.

### 12. Contact

Questions, privacy requests, and safety concerns can be sent to:

**Revio Team**  
**Email:** threvioapp@gmail.com

---

## 3. Terms of Use and Community Guidelines

**Effective date:** [REQUIRED BEFORE LAUNCH: DD Month YYYY]

### Revio Terms of Use and Community Guidelines

Welcome to Revio. By creating an account, uploading content, or using Revio, you agree to these Terms of Use and Community Guidelines.

#### 1. Eligibility and account responsibility

You must be at least 16 years old to use Revio. Provide accurate account information, keep your credentials secure, and do not create accounts for other people without permission.

#### 2. Your content

You keep ownership of the content you create. You grant Revio a non-exclusive, worldwide, royalty-free licence to host, store, reproduce, adapt for technical purposes, display, and distribute your content solely to operate, improve, and promote the Services. This licence ends when the content is deleted, except for limited backup, legal, security, or de-identified uses described in the Privacy Policy.

You must have the rights needed to upload every photograph and other item you post. Do not upload content that infringes intellectual-property, privacy, or other rights.

#### 3. Prohibited content and conduct

Do not post, upload, comment, or otherwise engage in conduct that:

- is illegal, fraudulent, deceptive, or unsafe;
- contains child sexual abuse or exploitation material, sexualises minors, or facilitates the exploitation, grooming, trafficking, or endangerment of a child;
- includes threats, harassment, bullying, hate speech, doxxing, stalking, or encouragement of violence or self-harm;
- contains explicit sexual content, graphic violence, or content that is otherwise inappropriate for the Revio community;
- infringes copyright, trademark, privacy, publicity, or other rights;
- impersonates another person or misrepresents an affiliation;
- reveals another person's precise location, personal data, or confidential information without permission;
- distributes malware, spam, scams, or attempts to access accounts or systems without authorisation; or
- interferes with moderation, reporting, security controls, or the ordinary operation of Revio.

#### 4. Reporting, blocking, and moderation

Use the in-app reporting tools to report content or users that violate these rules. You can block a user using the in-app blocking tools. Revio may review reports and take action, including removing content, limiting features, suspending, or permanently terminating an account. We may act without prior notice where needed to protect users or comply with law.

For urgent safety concerns or questions, contact threvioapp@gmail.com.

#### 5. Account deletion

You can delete your account from Settings → Account → Delete account or use the public deletion-request webpage described in the Privacy Policy. Account deletion is permanent.

#### 6. Suspension or termination

We may suspend or terminate accounts and remove content that violates these Terms, threatens users or the Services, or creates legal or security risk.

#### 7. Changes and contact

We may update these Terms. Continued use after an update means you accept the updated Terms, to the extent permitted by law. Contact us at threvioapp@gmail.com.

---

## 4. Child Safety Standards

### Revio Child Safety Standards

Revio has zero tolerance for child sexual abuse and exploitation (CSAE) and child sexual abuse material (CSAM). We prohibit any content or behaviour that sexualises, exploits, grooms, traffics, abuses, or endangers children.

Users can report content and users in the app. We review reports and may remove content, suspend or terminate accounts, preserve relevant information where legally required, and report apparent CSAM or child exploitation to appropriate authorities or designated reporting organisations as required by law.

**Child Safety Point of Contact:** threvioapp@gmail.com

---

## 5. In-app copy

### Privacy Policy settings row

**Privacy Policy**  
Learn how Revio collects, uses, and protects your data.

### Terms settings row

**Terms & Community Guidelines**  
Rules for using Revio and keeping the community safe.

### Mandatory acceptance checkbox

`[ ] I am at least 16 years old and I agree to the Terms of Use, Community Guidelines, and Privacy Policy.`

Links: **Terms of Use** · **Community Guidelines** · **Privacy Policy**

### Location permission education screen

**Add a location to your post?**

Location is optional. If you allow it, Revio uses your location only for this post and shows other users at most your town and country — never your precise coordinates. You can post without a location at any time.

Buttons: **Continue without location** / **Allow location**

### Report sheet

**Report content or user**

Help us keep Revio safe. Tell us what is wrong and we will review the report. Do not use reports for disagreements or content you simply dislike.

Buttons: **Report post** / **Report comment** / **Report user**

### Block confirmation sheet

**Block @username?**

You will no longer see this person's posts or comments. They will not be told that you blocked them. You can manage blocked users in Settings.

Buttons: **Cancel** / **Block user**

### Delete-account screen

**Delete your account?**

This permanently deletes your Revio account, profile, posts, comments, likes, reports, sessions, and stored images. You will lose access to your account and this action cannot be undone.

`[ ] I understand that my account and data will be permanently deleted.`

Buttons: **Cancel** / **Delete account permanently**

### Analytics consent copy — use only if consent is implemented

`[REQUIRED BEFORE LAUNCH: Firebase Analytics is not integrated in the app today. Do not ship this screen until the SDK exists — otherwise it asks for consent to a feature that doesn't run.]`

**Help improve Revio**

Allow anonymous app-usage analytics to help us understand performance and improve features. Analytics are not used for advertising. You can change this choice later in Settings.

Buttons: **Not now** / **Allow analytics**

---

## 6. Public web account-deletion page copy

**Page title:** Delete your Revio account

### Request account deletion

You can request deletion of your Revio account and associated personal data. Deleting your account permanently removes your profile, posts, comments, likes, reports, account sessions, and Revio-controlled images. This action cannot be undone.

To submit a request, enter the email address associated with your account. We may ask you to verify ownership before processing the request.

**Email address**  
**Submit deletion request**

We will confirm receipt and tell you the expected completion date. Limited data may be retained only where required by law or necessary for security, dispute resolution, or fraud prevention. De-identified aggregate analytics may remain where it cannot reasonably identify you.

For help, contact threvioapp@gmail.com.

---

## 7. Google Play / App Store submission checklist

### Google Play

- [ ] Select the appropriate Social app category and complete the UGC/content rating questions accurately.
- [ ] Submit the Child Safety Standards declaration and publish the Child Safety Standards page above.
- [ ] Submit Data Safety disclosures matching the final build, including account/profile data, photos, precise location, user content, identifiers, usage data, and analytics.
- [ ] State that data is encrypted in transit only if the production build and backend use HTTPS/TLS throughout.
- [ ] State account deletion is available only after both the in-app flow and public deletion URL are live.
- [ ] Set the target audience to 16+ and do not include children.
- [ ] Provide reviewer credentials and clear instructions to reach posting, reporting, blocking, and deletion flows.

### App Store — when iOS begins

- [ ] Add the public Privacy Policy URL in App Store Connect.
- [ ] Complete App Privacy answers based on the iOS build and every third-party SDK used by it.
- [ ] Declare public UGC/social-media capabilities and complete the current age rating questionnaire.
- [ ] Provide in-app account deletion, reporting, blocking, moderation, and published support contact details.
- [ ] Re-check whether Apple Sign In is required if Google or another third-party sign-in option is offered on iOS.

---

## 8. Implementation notes

- The application currently has placeholder navigation targets for Privacy Policy and Terms & Conditions. Replace them with screens that render or open the published URLs before launch.
- Put a visible **Block user** action on the profile, post options menu, and comment options menu after the backend and filtering logic exist.
- Keep this file updated when adding new SDKs, notifications, direct messages, purchases, ads, contacts, friendships, or any new data use. Each of those can change the Privacy Policy and store disclosures.
