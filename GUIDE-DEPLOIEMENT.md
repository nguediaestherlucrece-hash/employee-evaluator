# Guide de déploiement — EvalPro
## Système d'évaluation discrète des employés

---

## 📋 Architecture du projet

```
employee-evaluator/
├── src/main/java/com/evaluator/
│   ├── EmployeeEvaluatorApplication.java     ← Point d'entrée
│   ├── controller/
│   │   ├── EvaluationController.java         ← Toutes les routes web
│   │   └── AuthController.java               ← Login/logout
│   ├── service/
│   │   ├── EvaluationService.java            ← Logique métier principale
│   │   ├── GroqService.java                  ← Appels API GROQ (IA)
│   │   ├── CvParserService.java              ← Extraction texte PDF/DOCX
│   │   └── RapportPdfService.java            ← Génération rapport PDF
│   ├── model/
│   │   ├── Employee.java                     ← Entité employé
│   │   └── EvaluationSession.java            ← Entité session d'évaluation
│   ├── repository/
│   │   ├── EmployeeRepository.java
│   │   └── EvaluationSessionRepository.java
│   ├── dto/
│   │   └── ReponseSubmitDTO.java
│   └── config/
│       └── SecurityConfig.java               ← Spring Security
├── src/main/resources/
│   ├── templates/
│   │   ├── fragments/layout.html             ← Sidebar + layout de base
│   │   ├── login.html
│   │   ├── dashboard.html
│   │   ├── examiner/
│   │   │   ├── upload-cv.html               ← Étape 1
│   │   │   ├── questions.html               ← Étape 2
│   │   │   ├── saisie-reponses.html         ← Étape 3
│   │   │   └── sessions.html                ← Historique
│   │   └── report/rapport.html              ← Étape 4 + téléchargement PDF
│   └── application.properties
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

---

## 🔄 Flux d'utilisation (4 étapes)

```
1. Upload CV (PDF/DOCX)
   ↓
   CvParserService extrait le texte
   ↓
   GroqService génère N questions naturelles
   ↓
2. L'examinateur consulte les questions
   (les pose discrètement à l'employé)
   ↓
3. L'examinateur saisit les réponses orales
   ↓
   GroqService analyse tout et génère un rapport JSON
   ↓
4. Rapport affiché (score, niveau, forces, recommandations)
   + Téléchargement PDF (iText7)
```

---

## 🔑 APIs utilisées (toutes gratuites)

| API / Bibliothèque | Usage | Gratuit |
|--------------------|-------|---------|
| **GROQ API** | Génération de questions + analyse des réponses | ✅ Oui (30 req/min, 14400/jour) |
| **Apache PDFBox** | Extraction texte des CVs PDF | ✅ Open source (Apache 2.0) |
| **Apache POI** | Extraction texte des CVs DOCX | ✅ Open source (Apache 2.0) |
| **iText7** | Génération du rapport PDF | ✅ Open source (AGPL) |
| **Spring Boot** | Framework web + sécurité | ✅ Open source |
| **H2 / PostgreSQL** | Base de données | ✅ Open source |

---

## 🚀 DÉPLOIEMENT OPTION 1 — En local (développement)

### Prérequis
- Java 21+
- Maven 3.9+
- Compte GROQ gratuit : https://console.groq.com

### Étape 1 : Obtenir la clé GROQ
1. Aller sur https://console.groq.com
2. Créer un compte (gratuit)
3. Menu "API Keys" → "Create API Key"
4. Copier la clé (commence par `gsk_...`)

### Étape 2 : Configurer l'application
Modifier `src/main/resources/application.properties` :
```properties
groq.api.key=gsk_VOTRE_CLE_ICI
```
OU définir la variable d'environnement :
```bash
export GROQ_API_KEY=gsk_VOTRE_CLE_ICI
```

### Étape 3 : Lancer l'application
```bash
cd employee-evaluator
mvn spring-boot:run
```

### Étape 4 : Accéder à l'application
- URL : http://localhost:8080
- Login : `admin` / `Admin@2024!`
- Console H2 (BDD dev) : http://localhost:8080/h2-console

---

## 🐳 DÉPLOIEMENT OPTION 2 — Docker (recommandé)

### Prérequis
- Docker + Docker Compose installés
- Clé API GROQ

### Étape 1 : Configurer les variables
```bash
cp .env.example .env
# Éditer .env et renseigner GROQ_API_KEY et les mots de passe
nano .env
```

### Étape 2 : Construire et lancer
```bash
docker-compose up -d --build
```

### Étape 3 : Vérifier que tout tourne
```bash
docker-compose ps
docker-compose logs -f app
```

### Étape 4 : Accéder
- URL : http://localhost:8080
- Arrêt : `docker-compose down`
- Arrêt + suppression données : `docker-compose down -v`

---

## ☁️ DÉPLOIEMENT OPTION 3 — Cloud gratuit (Render.com)

Render.com offre un hébergement gratuit pour les apps web Java.

### Étape 1 : Pousser le code sur GitHub
```bash
git init
git add .
git commit -m "Initial commit EvalPro"
git remote add origin https://github.com/VOTRE_USER/employee-evaluator.git
git push -u origin main
```

### Étape 2 : Créer le service sur Render
1. Aller sur https://render.com (compte gratuit)
2. "New +" → "Web Service"
3. Connecter votre repo GitHub
4. Configurer :
   - **Runtime** : Docker
   - **Branch** : main
   - **Plan** : Free

### Étape 3 : Variables d'environnement sur Render
Dans le dashboard Render → "Environment" :
```
GROQ_API_KEY          = gsk_votre_cle
ADMIN_USERNAME        = admin
ADMIN_PASSWORD        = VotreMotDePasse
SPRING_DATASOURCE_URL = (utiliser l'URL de la BDD Render PostgreSQL)
```

### Étape 4 : Créer la base PostgreSQL sur Render
1. "New +" → "PostgreSQL"
2. Plan Free
3. Copier l'Internal Database URL
4. Coller dans `SPRING_DATASOURCE_URL`

> ⚠️ Le plan gratuit Render met l'app en veille après 15min d'inactivité.
> Pour usage professionnel, prévoir le plan Starter (7$/mois).

---

## 🔧 Configuration production (application-prod.properties)

Pour la production, créer `src/main/resources/application-prod.properties` :
```properties
# Base de données PostgreSQL
spring.datasource.url=${SPRING_DATASOURCE_URL}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect

# Désactiver H2
spring.h2.console.enabled=false

# Logging réduit
logging.level.com.evaluator=INFO

# Upload
app.upload.dir=/app/uploads/cvs
```

---

## 🔒 Sécurité — Points importants

1. **Ne jamais committer la clé GROQ** dans Git
   - Ajouter `.env` au `.gitignore`
   - Toujours utiliser des variables d'environnement

2. **Changer les mots de passe par défaut** avant toute mise en production

3. **HTTPS obligatoire** en production
   - Render/Heroku/Railway activent HTTPS automatiquement
   - En auto-hébergement : utiliser Nginx + Let's Encrypt (Certbot)

4. **Ajouter un `.gitignore`** :
```
target/
.env
uploads/
*.jar
.idea/
```

---

## 🐛 Dépannage fréquent

| Problème | Solution |
|----------|----------|
| `GROQ API error: 401` | Clé GROQ invalide ou expirée → en regénérer une |
| `GROQ API error: 429` | Limite de taux dépassée → attendre 1 minute |
| PDF illisible | CV scanné (image) non supporté → demander un PDF texte |
| `Port 8080 déjà utilisé` | Changer `server.port=8081` dans application.properties |
| Rapport JSON non parsé | Le modèle GROQ a répondu en dehors du format JSON → relancer |

---

## 📈 Améliorations possibles (roadmap)

- [ ] Gestion multi-utilisateurs avec rôles (ADMIN, EXAMINATEUR)
- [ ] Envoi du rapport par email (Spring Mail + Mailtrap gratuit)
- [ ] Comparaison entre plusieurs évaluations du même employé
- [ ] Export Excel des résultats (Apache POI)
- [ ] Authentification OAuth2 (Google / Microsoft)
- [ ] Audit trail (qui a évalué qui, quand)
