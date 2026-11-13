# Phrasomètre

Compteur de phrases mensuelles (Android, Kotlin + Jetpack Compose, Firebase).

## Compiler avec GitHub
1. Créez un dépôt GitHub et envoyez tout le contenu de ce dossier.
2. Onglet **Actions** → « Build APK Phrasomètre » → *Run workflow* (ou simple push).
3. Téléchargez l'artefact **Phrasometre-APK** (fichier `app-release.apk`) et installez-le.

## Firebase
- Package : `mg.sitraka.phrasometre`
- Le keystore `app/phrasometre.jks` est fixe. SHA-1 : `A6:1F:27:A4:73:85:66:0C:8D:E4:91:B7:02:F1:7B:9D:46:4D:4D:AA`
- Si vous modifiez les empreintes SHA dans Firebase, retéléchargez `google-services.json` et remplacez `app/google-services.json`.
- Règles Firestore : accès limité à `users/{uid}` pour l'utilisateur connecté.
