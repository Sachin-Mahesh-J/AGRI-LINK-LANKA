import { getApps, initializeApp } from 'firebase/app'
import { getAuth } from 'firebase/auth'
import { getFirestore } from 'firebase/firestore'
import { getStorage } from 'firebase/storage'

const environmentConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
}

export const firebaseConfigured = Object.values(environmentConfig).every(Boolean)
const firebaseConfig = firebaseConfigured ? environmentConfig : {
  apiKey: 'demo-api-key',
  authDomain: 'demo.invalid',
  projectId: 'demo-project',
  storageBucket: 'demo-project.invalid',
  messagingSenderId: '000000000000',
  appId: '1:000000000000:web:0000000000000000000000',
}
export const firebaseApp = getApps().find((app) => app.name === '[DEFAULT]')
  ?? initializeApp(firebaseConfig)
export const auth = getAuth(firebaseApp)
export const db = getFirestore(firebaseApp)
export const storage = getStorage(firebaseApp)
const provisioningApp = getApps().find((app) => app.name === 'user-provisioning')
  ?? initializeApp(firebaseConfig, 'user-provisioning')
export const provisioningAuth = getAuth(provisioningApp)
