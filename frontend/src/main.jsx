import React, { useEffect, useMemo, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './main.css';
import '../styles.css';

const icon = {
  dashboard: '⌂',
  scan: '▧',
  history: '◷',
  rules: '☷',
  insights: '▥',
  settings: '⚙',
  help: '?'
};

const checklistExamples = [
  { code: 'PRODUCT_IDENTITY', icon: '▤', title: 'Product identity', prompt: 'Can the product name be found and read clearly?' },
  { code: 'INGREDIENT_INFORMATION', icon: '⌕', title: 'Ingredient information', prompt: 'Check the ingredient and allergen text against the approved product facts.' },
  { code: 'NUTRITION_INFORMATION', icon: '◉', title: 'Nutrition information', prompt: 'Review the nutrition panel and its legibility.' },
  { code: 'NET_QUANTITY', icon: '⚖', title: 'Net quantity', prompt: 'Confirm the declared quantity against the product record.' },
  { code: 'BUSINESS_DETAILS', icon: '⌂', title: 'Business details', prompt: 'Look for manufacturer or marketer details to verify.' },
  { code: 'ON_PACK_CLAIMS', icon: '“', title: 'On-pack claims', prompt: 'Compare claims with approved product facts; uncertain wording needs a person.' }
];

const pageNames = {
  dashboard: 'Dashboard',
  scan: 'Scan product',
  history: 'Review history',
  rules: 'Checklist',
  insights: 'Insights',
  settings: 'Settings',
  help: 'Help & support'
};

const dateLabel = value =>
  value
    ? new Date(value).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
    : '—';

function Logo() {
  return (
    <span className="brand-mark" aria-hidden="true">
      <svg viewBox="0 0 48 48" fill="none">
        <path d="M24 3 41 9v12c0 11.6-7.5 19.4-17 24C14.5 40.4 7 32.6 7 21V9l17-6Z" stroke="currentColor" strokeWidth="2.8" />
        <path d="M15.5 21.5c4.3-.2 7.4 2.8 8.5 7.3 1.1-4.5 4.2-7.5 8.5-7.3-1 6.7-4.1 10.1-8.5 10.8-4.4-.7-7.5-4.1-8.5-10.8Z" fill="currentColor" />
        <path d="M24 16v14" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
      </svg>
    </span>
  );
}

function BarcodeScanner({ onDetected, onClose }) {
  const videoRef = useRef(null);
  const detectedRef = useRef(onDetected);
  const [error, setError] = useState('');
  detectedRef.current = onDetected;

  useEffect(() => {
    let active = true;
    let controls;

    const startScanner = async () => {
      if (!navigator.mediaDevices?.getUserMedia) {
        setError('Camera access is not supported in this browser. Enter the barcode number below.');
        return;
      }

      const video = videoRef.current;
      if (!video) return;
      const [{ BrowserMultiFormatReader }, { BarcodeFormat }] = await Promise.all([
        import('@zxing/browser'),
        import('@zxing/library')
      ]);
      if (!active) return;
      const reader = new BrowserMultiFormatReader();
      reader.possibleFormats = [
        BarcodeFormat.EAN_8,
        BarcodeFormat.EAN_13,
        BarcodeFormat.UPC_A,
        BarcodeFormat.ITF
      ];
      controls = await reader.decodeFromConstraints(
        { audio: false, video: { facingMode: { ideal: 'environment' } } },
        video,
        result => {
          if (!active || !result) return;
          const value = result.getText();
          if (/^[0-9]{8,14}$/.test(value)) {
            detectedRef.current(value);
          }
        }
      );
      if (!active) controls.stop();
    };

    startScanner().catch(() => {
      if (active) setError('Camera access was blocked or unavailable. Allow camera access, or enter the barcode below.');
    });

    return () => {
      active = false;
      controls?.stop();
      if (videoRef.current) videoRef.current.srcObject = null;
    };
  }, []);

  return (
    <div className="barcode-camera">
      {!error && <video ref={videoRef} className="barcode-video" muted playsInline aria-label="Live product barcode camera" />}
      <div className="barcode-camera-actions">
        <span className="field-hint">{error || 'Point the camera at the product barcode and hold steady.'}</span>
        <button className="button button-quiet button-small" type="button" onClick={onClose}>Close camera</button>
      </div>
    </div>
  );
}

function ProductArtwork({ kind = 'label' }) {
  const packageName = {
    chips: 'Snack packet',
    milk: 'Milk carton',
    noodles: 'Noodle cup',
    label: 'Package label'
  }[kind] || 'Package label';

  return (
    <svg className={`package-art package-art-${kind}`} viewBox="0 0 60 72" fill="none" aria-hidden="true">
      <title>{packageName}</title>
      {kind === 'chips' && <>
        <path d="m12 7 36 2 3 8-2 43-7 6-33-2-3-7 3-44 3-6Z" fill="#F5B91F" stroke="#FFE28A" strokeWidth="1.5" />
        <path d="m12 8 36 2 2 7-38-2-3-1 3-6Z" fill="#E87513" />
        <path d="m10 22 39 2-1 8-39-2 1-8Z" fill="#F8D857" opacity=".8" />
        <path d="M16 38c5-6 10-6 14 0s9 6 14 0M15 46c5-6 10-6 14 0s9 6 14 0" stroke="#FFF0AE" strokeWidth="2" strokeLinecap="round" />
        <path d="M18 20c4-5 10-6 15-2-1 6-5 9-10 9-3-1-5-3-5-7Z" fill="#FFF5D1" />
        <text x="26" y="25" fill="#9A3918" fontSize="5" fontWeight="800" textAnchor="middle">CLASSIC</text>
        <text x="29" y="57" fill="#713A17" fontSize="4" fontWeight="700" textAnchor="middle">SALTED</text>
      </>}
      {kind === 'milk' && <>
        <path d="m14 18 7-8h18l8 8v43l-5 5H17l-3-5V18Z" fill="#F4F8FF" stroke="#A9D7ED" strokeWidth="1.5" />
        <path d="m21 10 5 8v48M39 10l-3 8v48M14 18h33" stroke="#B6DCEF" strokeWidth="1.2" />
        <path d="M23 10h13l-2 7h-9l-2-7Z" fill="#46B8F4" />
        <path d="M17 33c7-4 17-4 27 0v12c-10-4-20-4-27 0V33Z" fill="#D6F1FF" />
        <text x="30" y="29" fill="#1682B6" fontSize="5" fontWeight="800" textAnchor="middle">FRESH</text>
        <text x="30" y="40" fill="#1675A0" fontSize="6" fontWeight="800" textAnchor="middle">MILK</text>
        <text x="30" y="55" fill="#4B829E" fontSize="4" fontWeight="600" textAnchor="middle">TONED</text>
      </>}
      {kind === 'noodles' && <>
        <path d="M10 22h40l-4 35c-1 6-5 9-16 9s-15-3-16-9l-4-35Z" fill="#E44C2E" stroke="#FFAE69" strokeWidth="1.5" />
        <path d="M8 18c0-4 10-7 22-7s22 3 22 7v5H8v-5Z" fill="#FFC94B" stroke="#FFE19A" strokeWidth="1.5" />
        <path d="M14 31c8-4 24-4 32 0M15 54c8 4 22 4 30 0" stroke="#FF8A4F" strokeWidth="2" />
        <circle cx="30" cy="40" r="10" fill="#FFF1C7" />
        <path d="M23 40c3-7 5 7 8 0s5 7 8 0" stroke="#E8A72C" strokeWidth="2" strokeLinecap="round" />
        <text x="30" y="28" fill="#8E321E" fontSize="4.5" fontWeight="800" textAnchor="middle">NOODLES</text>
      </>}
      {kind === 'label' && <>
        <path d="M14 8h26l7 7v49H14V8Z" fill="#E8F1EB" stroke="#9BB6A5" strokeWidth="1.5" />
        <path d="M40 8v8h7M19 23h21M19 28h23M19 34h17M19 42h22M19 47h19M19 54h21" stroke="#6B8C78" strokeWidth="2" strokeLinecap="round" />
        <path d="M19 59h11" stroke="#46A96A" strokeWidth="3" strokeLinecap="round" />
      </>}
    </svg>
  );
}

function ProductDetailsStatus({ product }) {
  const missingFields = [
    ['Product name', product.productName],
    ['Brand', product.brands],
    ['Net quantity', product.quantity],
    ['Ingredients', product.ingredients],
    ['Nutrition values', Object.keys(product.nutrients || {}).length > 0]
  ].filter(([, value]) => !value).map(([label]) => label);
  const complete = missingFields.length === 0;

  return (
    <div className={`product-detail-status ${complete ? 'complete' : 'incomplete'}`} role="status">
      <span className="product-detail-status-icon" aria-hidden="true">{complete ? '✓' : '!'}</span>
      <div>
        <strong>{complete ? 'Product details available' : 'Product details incomplete'}</strong>
        <p>{complete
          ? 'The database record contains the main product details. This does not mean the product passed an FSSAI check.'
          : `Not listed in the database: ${missingFields.join(', ')}. This does not mean the product is bad; verify the package label.`}</p>
      </div>
    </div>
  );
}

function AuthScreen({ mode, onModeChange, onSubmit, busy, error }) {
  const signingUp = mode === 'signup';
  return (
    <main className="auth-page">
      <section className="auth-card">
        <div className="auth-brand"><Logo /><span><strong>HUNTER</strong><small>LABEL REVIEW WORKSPACE</small></span></div>
        <div className="eyebrow">{signingUp ? 'Create your workspace account' : 'Welcome back'}</div>
        <h1>{signingUp ? 'Create your account' : 'Sign in to HUNTER'}</h1>
        <p className="auth-intro">Your reviews are private to your account. Sign in to continue to the label review workspace.</p>
        <form className="auth-form" onSubmit={onSubmit}>
          {signingUp && <label className="auth-field">Full name<input name="displayName" type="text" autoComplete="name" maxLength={140} required /></label>}
          <label className="auth-field">Email address<input name="email" type="email" autoComplete="email" maxLength={80} required /></label>
          <label className="auth-field">Password<input name="password" type="password" autoComplete={signingUp ? 'new-password' : 'current-password'} minLength={signingUp ? 12 : undefined} maxLength={72} required /><small>{signingUp ? 'Use at least 12 characters.' : 'Your password is case-sensitive.'}</small></label>
          {error && <div className="auth-error" role="alert">{error}</div>}
          <button className="button button-primary auth-submit" type="submit" disabled={busy}>{busy ? 'Please wait…' : signingUp ? 'Create account' : 'Sign in'}</button>
        </form>
        <p className="auth-switch">{signingUp ? 'Already have an account?' : 'New to HUNTER?'} <button type="button" onClick={() => onModeChange(signingUp ? 'login' : 'signup')}>{signingUp ? 'Sign in' : 'Create an account'}</button></p>
        <p className="auth-security">Passwords are stored as one-way hashes. Sessions use secure, server-managed cookies.</p>
      </section>
    </main>
  );
}

function App() {
  const [page, setPage] = useState('dashboard');
  const [user, setUser] = useState(null);
  const [authChecked, setAuthChecked] = useState(false);
  const [authMode, setAuthMode] = useState('login');
  const [authBusy, setAuthBusy] = useState(false);
  const [authError, setAuthError] = useState('');
  const [mobileOpen, setMobileOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [reviews, setReviews] = useState([]);
  const [rules, setRules] = useState([]);
  const [capabilities, setCapabilities] = useState(null);
  const [backendError, setBackendError] = useState('');
  const [notice, setNotice] = useState('');
  const [reviewFilter, setReviewFilter] = useState('all');
  const [selectedReview, setSelectedReview] = useState(null);
  const [upload, setUpload] = useState(null);
  const [uploadPreview, setUploadPreview] = useState('');
  const [barcode, setBarcode] = useState('');
  const [barcodeScannerOpen, setBarcodeScannerOpen] = useState(false);
  const [barcodeLookupBusy, setBarcodeLookupBusy] = useState(false);
  const [barcodeLookupError, setBarcodeLookupError] = useState('');
  const [productDetails, setProductDetails] = useState(null);
  const [productName, setProductName] = useState('');
  const [category, setCategory] = useState('packaged-snacks');
  const [labelText, setLabelText] = useState('');
  const [approvedFacts, setApprovedFacts] = useState('');
  const [busy, setBusy] = useState(false);
  const [decisionReason, setDecisionReason] = useState('');
  const [dragOver, setDragOver] = useState(false);
  const [aiConsent, setAiConsent] = useState(false);
  const [ocrBusy, setOcrBusy] = useState(false);
  const [aiBusy, setAiBusy] = useState(false);
  const [aiSuggestion, setAiSuggestion] = useState('');
  const cameraInput = useRef(null);
  const toastTimer = useRef(null);

  const notify = message => {
    setNotice(message);
    window.clearTimeout(toastTimer.current);
    toastTimer.current = window.setTimeout(() => setNotice(''), 4000);
  };

  const request = async (path, options = {}) => {
    let response;
    try {
      const method = (options.method || 'GET').toUpperCase();
      const headers = new Headers(options.headers || {});
      if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
        const csrfResponse = await fetch('/api/auth/csrf', { credentials: 'same-origin' });
        if (!csrfResponse.ok) throw new Error('Could not establish a secure session. Refresh and try again.');
        const csrf = await csrfResponse.json();
        headers.set('X-XSRF-TOKEN', csrf.token);
      }
      response = await fetch(`/api${path}`, {
        ...options,
        headers,
        credentials: 'same-origin'
      });
    } catch (error) {
      throw new Error(error.message || 'The review API is unavailable. Start the Spring Boot backend and try again.');
    }
    if (!response.ok) {
      const problem = await response.json().catch(() => null);
      if (response.status === 401 && !['/auth/login', '/auth/signup', '/auth/me'].includes(path)) {
        setUser(null);
        setReviews([]);
        setSelectedReview(null);
        setAuthMode('login');
      }
      if (path.startsWith('/products/barcode/')) {
        if (response.status === 400) {
          throw new Error('Enter a valid product barcode and try again.');
        }
        if (response.status === 404) {
          throw new Error('No Open Food Facts record was found for this barcode. Check the code or enter product details manually.');
        }
        if (response.status >= 500) {
          throw new Error(problem?.detail || problem?.message || 'Product lookup is unavailable. Check your internet connection or enter product details manually.');
        }
      }
      const error = new Error(problem?.detail || problem?.message || (
        response.status >= 500
          ? 'The review API returned an error. Confirm Spring Boot is running at 127.0.0.1:8080.'
          : `Request failed (${response.status}).`
      ));
      error.status = response.status;
      throw error;
    }
    return response.status === 204 ? null : response.json();
  };

  const refresh = async () => {
    const [reviewResult, ruleResult, capabilityResult] = await Promise.all([
      request('/reviews'),
      request('/rules'),
      request('/capabilities')
    ]);
    setReviews(reviewResult);
    setRules(ruleResult);
    setCapabilities(capabilityResult);
    setBackendError('');
  };

  useEffect(() => {
    let active = true;
    request('/auth/me')
      .then(account => {
        if (!active) return;
        setUser(account);
        return refresh();
      })
      .catch(error => {
        if (!active) return;
        if (error.status !== 401) setBackendError(error.message);
      })
      .finally(() => {
        if (active) setAuthChecked(true);
      });
    return () => {
      active = false;
      window.clearTimeout(toastTimer.current);
    };
  }, []);

  const handleAuthSubmit = async event => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const values = {
      email: String(form.get('email') || '').trim(),
      password: String(form.get('password') || '')
    };
    setAuthBusy(true);
    setAuthError('');
    try {
      if (authMode === 'signup') {
        values.displayName = String(form.get('displayName') || '').trim();
        await request('/auth/signup', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(values)
        });
      }
      const account = await request('/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: values.email, password: values.password })
      });
      await refresh();
      setUser(account);
    } catch (error) {
      setAuthError(error.message);
    } finally {
      setAuthBusy(false);
    }
  };

  const handleLogout = async () => {
    try {
      await request('/auth/logout', { method: 'POST' });
      setUser(null);
      setReviews([]);
      setSelectedReview(null);
      setUpload(null);
      setUploadPreview('');
      setAiSuggestion('');
      setProfileOpen(false);
      setAuthMode('login');
    } catch (error) {
      notify(error.message);
    }
  };

  useEffect(() => {
    if (!upload) {
      setUploadPreview('');
      return undefined;
    }
    const preview = URL.createObjectURL(upload);
    setUploadPreview(preview);
    return () => URL.revokeObjectURL(preview);
  }, [upload]);

  useEffect(() => {
    const onKeyDown = event => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        document.querySelector('#global-search')?.focus();
      }
      if (event.key === 'Escape') {
        setProfileOpen(false);
        setMobileOpen(false);
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, []);

  const navigate = nextPage => {
    setPage(nextPage);
    setMobileOpen(false);
    setProfileOpen(false);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const chooseImage = (file, keepProductDetails = false) => {
    if (!file) return;
    if (!['image/png', 'image/jpeg'].includes(file.type)) {
      notify('Choose a PNG or JPEG image.');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      notify('This image is over 10 MB. Choose a smaller file.');
      return;
    }
    setUpload(file);
    if (!keepProductDetails || !productDetails) {
      setProductDetails(null);
      setBarcode('');
      setProductName(file.name.replace(/\.[^.]+$/, ''));
      setLabelText('');
    }
    setApprovedFacts('');
    setSelectedReview(null);
    setAiConsent(false);
    setAiSuggestion('');
    setPage('scan');
  };

  const lookupBarcode = async value => {
    const code = value.trim();
    setBarcode(code);
    setBarcodeScannerOpen(false);
    setBarcodeLookupError('');
    setBarcodeLookupBusy(true);
    setProductDetails(null);
    try {
      const product = await request(`/products/barcode/${encodeURIComponent(code)}`);
      const labelLines = [
        `Product name: ${product.productName || 'Not listed'}`,
        `Barcode: ${product.barcode}`,
        product.brands && `Brand: ${product.brands}`,
        product.quantity && `Net quantity: ${product.quantity}`,
        product.ingredients && `Ingredients: ${product.ingredients}`,
        product.allergens && `Allergens: ${product.allergens}`,
        product.categories && `Product categories (database): ${product.categories}`,
        ...Object.entries(product.nutrients || {}).map(([name, amount]) => `${name}: ${amount}`)
      ].filter(Boolean);
      setProductDetails(product);
      setUpload(null);
      setProductName(product.productName || '');
      setLabelText(labelLines.join('\n').slice(0, 30000));
      setApprovedFacts('');
      setSelectedReview(null);
      setAiConsent(false);
      setAiSuggestion('');
      setPage('scan');
      notify('Product record loaded. Verify it against the package before review.');
    } catch (error) {
      setBarcodeLookupError(error.message);
      notify(error.message);
    } finally {
      setBarcodeLookupBusy(false);
    }
  };

  const extractLabelText = async () => {
    if (!upload) return;
    setOcrBusy(true);
    try {
      const data = new FormData();
      data.append('image', upload);
      const result = await request('/ocr', { method: 'POST', body: data });
      setLabelText(result.text);
      notify(result.notice || 'OCR text extracted. Review and correct it before continuing.');
    } catch (error) {
      notify(error.message);
    } finally {
      setOcrBusy(false);
    }
  };

  const requestAiSuggestion = async review => {
    if (!aiConsent) return;
    setAiBusy(true);
    try {
      const result = await request('/ai/suggestions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          labelText: review.labelText,
          approvedFacts: review.approvedFacts || '',
          findings: (review.findings || []).map(finding => `${finding.title}: ${finding.detail}`)
        })
      });
      setAiSuggestion(result.text);
    } catch (error) {
      notify(error.message);
    } finally {
      setAiBusy(false);
    }
  };

  const submitReview = async event => {
    event.preventDefault();
    if (!upload) {
      notify('Choose a package label image before starting a review.');
      return;
    }
    if (!productName.trim()) {
      notify('Enter a product name to create the review.');
      return;
    }
    if (!labelText.trim()) {
      notify('Enter or correct the label text before checking.');
      return;
    }
    setBusy(true);
    try {
      const data = new FormData();
      data.append('image', upload);
      data.append('productName', productName.trim());
      data.append('category', category);
      data.append('labelText', labelText.trim());
      data.append('approvedFacts', approvedFacts.trim());
      const saved = await request('/reviews', { method: 'POST', body: data });
      setSelectedReview(saved);
      setUpload(null);
      setLabelText('');
      setApprovedFacts('');
      await refresh();
      notify('Review created. Findings are prompts for an authorized human reviewer.');
      document.querySelector('#review-findings')?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    } catch (error) {
      notify(error.message);
    } finally {
      setBusy(false);
    }
  };

  const openReview = async review => {
    try {
      setAiSuggestion('');
      setAiConsent(false);
      setSelectedReview(await request(`/reviews/${encodeURIComponent(review.id)}`));
      setPage('history');
    } catch (error) {
      notify(error.message);
    }
  };

  const decideReview = async decision => {
    if (!selectedReview) return;
    setBusy(true);
    try {
      const updated = await request(`/reviews/${encodeURIComponent(selectedReview.id)}/decisions`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          decision,
          reason: decisionReason.trim()
        })
      });
      setSelectedReview(updated);
      setDecisionReason('');
      await refresh();
      notify('Reviewer decision recorded in the review history.');
    } catch (error) {
      notify(error.message);
    } finally {
      setBusy(false);
    }
  };

  const filteredReviews = useMemo(() => {
    const search = query.trim().toLowerCase();
    return reviews.filter(review => {
      const matchesSearch = !search || `${review.productName} ${review.category} ${review.status}`.toLowerCase().includes(search);
      const matchesFilter = reviewFilter === 'all'
        || (reviewFilter === 'needs-review' && review.status === 'NEEDS_HUMAN_REVIEW')
        || (reviewFilter === 'reviewed' && ['APPROVED', 'NEEDS_CHANGES'].includes(review.status));
      return matchesSearch && matchesFilter;
    });
  }, [reviews, query, reviewFilter]);

  const pendingCount = reviews.filter(review => review.status === 'NEEDS_HUMAN_REVIEW').length;
  const nav = (
    <nav className="primary-nav" aria-label="Main navigation">
      {Object.entries(icon).slice(0, 5).map(([key, glyph]) => (
        <button key={key} className={`nav-link ${page === key ? 'active' : ''}`} type="button" onClick={() => navigate(key)} aria-current={page === key ? 'page' : undefined}>
          <span className="nav-icon" aria-hidden="true">{glyph}</span><span>{pageNames[key]}</span>{key === 'scan' && <span className="nav-arrow">↗</span>}
        </button>
      ))}
    </nav>
  );

  const renderActivity = () => {
    if (reviews.length) {
      const shown = reviews.slice(0, 3).filter(review => !query || review.productName.toLowerCase().includes(query.toLowerCase()));
      return shown.length ? shown.map(review => (
        <button className="activity-row activity-button" key={review.id} type="button" onClick={() => openReview(review)}>
          <span className="product-art real-product"><ProductArtwork /><img className="activity-thumb-image" src={`/api/reviews/${encodeURIComponent(review.id)}/image`} alt="" loading="lazy" /></span>
          <span><span className="activity-name">{review.productName}</span><span className="activity-description">{dateLabel(review.createdAt)} · LOCAL RECORD</span></span>
          <span className={`row-status ${review.status === 'APPROVED' ? 'done' : ''}`}>{statusLabel(review.status)}</span>
        </button>
      )) : <div className="empty-state">No matching reviews in this view.</div>;
    }
    return <div className="empty-state activity-empty"><span className="empty-state-mark"><Logo /></span><strong>No reviews yet</strong><span>Your real label reviews will appear here after you start a scan.</span><button className="text-link" type="button" onClick={() => navigate('scan')}>Create the first review →</button></div>;
  };

  const renderDropZone = (large = false) => (
    <div
      className={`drop-zone ${large ? 'scan-drop' : ''} ${dragOver ? 'drag-over' : ''}`}
      onDragEnter={event => { event.preventDefault(); setDragOver(true); }}
      onDragOver={event => event.preventDefault()}
      onDragLeave={event => {
        if (!event.currentTarget.contains(event.relatedTarget)) setDragOver(false);
      }}
      onDrop={event => {
        event.preventDefault();
        setDragOver(false);
        chooseImage(event.dataTransfer.files[0]);
      }}
    >
      <span className="scan-orbit" aria-hidden="true">⌗</span>
      <strong>Scan barcode or capture the package label</strong>
      <p>Scan the barcode for product details, then capture a clear package-label photo for OCR and review.</p>
      <div className="capture-actions">
        <button className="button button-primary button-small" type="button" onClick={() => cameraInput.current?.click()}>Scan product <span aria-hidden="true">▣</span></button>
        <button className="button button-secondary button-small" type="button" onClick={() => { setBarcodeScannerOpen(true); navigate('scan'); }}>Scan barcode <span aria-hidden="true">⌕</span></button>
      </div>
      <small>PNG or JPEG · up to 10 MB</small>
    </div>
  );

  const renderDashboard = () => (
    <>
      {backendError && <div className="notice backend-notice"><span className="notice-icon">!</span><span>{backendError} Run <code>mvn spring-boot:run</code> from the <code>backend</code> folder.</span></div>}
      <section className="hero" aria-label="HUNTER label review">
        <div className="hero-content">
          <div className="hero-brand"><span className="hero-brand-mark">✳</span> HUNTER</div>
          <h2>Grocery Label<br /><span>Review Assistant</span></h2>
          <p className="hero-copy">Look up product details, scan the package label, and review it with a human in control.</p>
          <div className="hero-badge" aria-label="Safe Food Healthy India"><span>Safe Food</span><em>Healthy India</em></div>
          <div className="hero-actions">
            <button className="button button-primary" type="button" onClick={() => navigate('scan')}>Start scanning <span aria-hidden="true">→</span></button>
            <button className="button button-secondary" type="button" onClick={() => navigate('rules')}>Explore the checklist</button>
          </div>
        </div>
      </section>

      <section className="metric-grid" aria-label="Workspace overview">
        <article className="metric-card"><span className="metric-icon">▣</span><div className="metric-copy"><strong>{reviews.length.toString().padStart(2, '0')}</strong><span>Reviews stored in the workspace</span></div><span className="metric-meta">LOCAL API</span></article>
        <article className="metric-card"><span className="metric-icon warn">⌕</span><div className="metric-copy"><strong>{pendingCount.toString().padStart(2, '0')}</strong><span>Awaiting human review</span></div><span className="metric-meta">YOU DECIDE</span></article>
        <article className="metric-card"><span className="metric-icon blue">☷</span><div className="metric-copy"><strong>{rules.length.toString().padStart(2, '0')}</strong><span>Example checklist prompts</span></div><span className="metric-meta">UNVERIFIED</span></article>
        <article className="metric-card"><span className="metric-icon">↗</span><div className="metric-copy"><strong>{capabilities?.ocr?.status === 'AVAILABLE' ? '01' : '00'}</strong><span>Local Tesseract OCR</span></div><span className="metric-meta">{capabilities?.ocr?.status === 'AVAILABLE' ? 'ON DEVICE' : 'SETUP NEEDED'}</span></article>
      </section>

      <div className="dashboard-grid">
        <section className="panel activity-panel">
          <div className="panel-title"><h3>Recent scan activity</h3><button className="text-link" type="button" onClick={() => navigate('history')}>View all →</button></div>
          <div className="activity-list">{renderActivity()}</div>
          <p className="micro">{reviews.length ? 'Uploaded images are stored outside the static website.' : 'Example rows are illustrative and are not real review results.'}</p>
        </section>
        <section className="panel upload-panel">
          <div className="panel-title"><h3>Scan your product</h3><span>JPG · PNG</span></div>
          {renderDropZone()}
        </section>
        <section className="panel guide-panel">
          <div className="panel-title"><h3>Review checklist</h3><button className="text-link" type="button" onClick={() => navigate('rules')}>View all →</button></div>
          <div className="guide-list">{(rules.length ? rules : checklistExamples).slice(0, 4).map(rule => (
            <div className="guide-row" key={rule.code}><span className="guide-check">{rule.icon || '✓'}</span><div><strong>{rule.title}</strong><p>{rule.prompt}</p></div></div>
          ))}</div>
          <p className="guide-footnote">Sample prompts only—not official FSSAI requirements.</p>
        </section>
      </div>

      <section className="insight-strip"><span className="insight-shield" aria-hidden="true">✓</span><div><strong>Suggested edits and final decisions stay reviewable.</strong><p>Checks are deterministic, versioned prompts—not a compliance certification.</p></div><button className="button button-secondary button-small" type="button" onClick={() => navigate('help')}>How it works →</button></section>

      <section className="workflow-section"><div className="section-heading"><div><div className="eyebrow">A considered review process</div><h2>From package photo to a clearer decision.</h2><p>Each review step is traceable, with a qualified person in control.</p></div><span className="micro">THE HUNTER WORKFLOW</span></div>
        <div className="workflow-grid">
          {[
            ['01', 'Set the review context', 'Choose one grocery category and add the approved product facts and checklist version.'],
            ['02', 'Scan barcode and capture packaging', 'Look up product details, then keep the actual package label image available for a careful review.'],
            ['03', 'Confirm extracted text', 'Optional local OCR can draft the text; correct every line against the package before running checks.'],
            ['04', 'Explain each finding', 'Review the matched text, finding, prompt version, and source status.'],
            ['05', 'Revise with a reviewer', 'Keep reviewer approval or needs-changes decisions in the audit history.'],
            ['06', 'Keep a review record', 'Track the reviewer, outcome, and version with access-controlled records.']
          ].map(([number, title, description]) => <article className="workflow-card" key={number}><div className="workflow-top"><span className="workflow-number">STEP {number}</span><span className="workflow-icon">✓</span></div><h3>{title}</h3><p>{description}</p></article>)}
        </div>
      </section>

      <section className="focus-section"><div><div className="eyebrow">Source and service policy</div><h2>Trust must be verified, not assumed.</h2><p>Official FSSAI regulations are linked for the team to review and select. The sample checklist remains unverified. OCR runs locally; optional Azure AI suggestions require separate configuration and reviewer consent.</p><div className="official-source-links"><a className="reference-link" href="https://www.fssai.gov.in/docs/food-law/regulations/Comp_Labelling%20Display_Version%20VIII_09_09_2025.pdf" target="_blank" rel="noreferrer">FSSAI Labelling &amp; Display compendium (9 Sep 2025) ↗</a><a className="reference-link" href="https://www.fssai.gov.in/food-law/regulations/amendments/labelling-display" target="_blank" rel="noreferrer">Check FSSAI amendments ↗</a></div></div>
        <div className="focus-list"><span className="focus-item"><i>✓</i> Checklist source and version remain visible</span><span className="focus-item"><i>✓</i> Product facts remain separate from label text</span><span className="focus-item"><i>✓</i> OCR is processed locally when requested</span><span className="focus-item"><i>✓</i> Optional AI data transfers require consent</span><span className="focus-item"><i>✓</i> Human reviewer records the final decision</span><span className="focus-item"><i>✓</i> Images use controlled local file storage</span></div>
      </section>
      <footer className="page-footer"><span>HUNTER · ASSISTIVE PRE-CHECK · HUMAN REVIEW REQUIRED</span><span>DEMO WORKSPACE · 0.1</span></footer>
    </>
  );

  const renderScan = () => (
    <>
      <div className="page-intro"><div><div className="eyebrow">Review workspace · Step 1 of 3</div><h1>Scan a product label</h1><p>Scan the barcode, verify product details, capture the package label, and review the extracted text.</p></div><span className="demo-pill"><i /> LOCAL API</span></div>
      {backendError && <div className="notice backend-notice"><span className="notice-icon">!</span><span>{backendError}</span></div>}
      <div className="scan-layout">
        <form className="panel scan-card" onSubmit={submitReview}>
          <section className="barcode-panel" aria-label="Barcode product lookup">
            <div className="barcode-panel-heading"><div><h3>1. Scan product barcode</h3><p>Look up a community product record before checking the physical label.</p></div><span className="status-pill">ONLINE LOOKUP</span></div>
            <p className="barcode-privacy">The barcode number is sent to Open Food Facts for lookup. Product records may be incomplete or out of date.</p>
            <div className="barcode-input-row">
              <input className="text-field" type="text" inputMode="numeric" autoComplete="off" maxLength={14} value={barcode} onChange={event => { setBarcode(event.target.value); setBarcodeLookupError(''); if (productDetails) { setProductDetails(null); setProductName(''); setLabelText(''); setUpload(null); } }} placeholder="8, 12, 13 or 14 digit barcode" aria-label="Product barcode" />
              <button className="button button-secondary button-small" type="button" disabled={barcodeLookupBusy || !barcode.trim()} onClick={() => lookupBarcode(barcode)}>{barcodeLookupBusy ? 'Looking up…' : 'Find product'}</button>
              {upload && <button className="button button-quiet button-small" type="button" onClick={() => setBarcodeScannerOpen(true)}>Scan barcode</button>}
            </div>
            {barcodeScannerOpen && <BarcodeScanner onDetected={lookupBarcode} onClose={() => setBarcodeScannerOpen(false)} />}
            {barcodeLookupError && <p className="barcode-error" role="alert">{barcodeLookupError}</p>}
            {productDetails && <div className="barcode-product-result" aria-live="polite">
              <ProductDetailsStatus product={productDetails} />
              <strong>{productDetails.productName || 'Product name unavailable'}</strong>
              <span>{[productDetails.brands, productDetails.quantity, productDetails.barcode].filter(Boolean).join(' · ')}</span>
              <a className="reference-link" href={productDetails.sourceUrl} target="_blank" rel="noreferrer">Open Food Facts product record ↗</a>
              <p>{productDetails.notice} Next, add a clear photo of the actual package label; verify and correct the imported text before running the prompts.</p>
            </div>}
          </section>
          <div className="steps"><span className="step active"><i className="step-number">1</i> Scan product</span><span className="step-line" /><span className={`step ${upload ? 'active' : ''}`}><i className="step-number">2</i> Add label photo</span><span className="step-line" /><span className={`step ${selectedReview ? 'active' : ''}`}><i className="step-number">3</i> Review prompts</span></div>
          {upload ? <div className="file-preview">{uploadPreview ? <img src={uploadPreview} alt={`Preview of ${upload.name}`} /> : <div className="file-loading">Preparing image preview…</div>}<div className="file-meta"><span>{upload.name} · {(upload.size / 1024 / 1024).toFixed(2)} MB</span><button className="text-link" type="button" onClick={() => setUpload(null)}>Replace</button></div></div> : renderDropZone(true)}
          {upload && <>
            <label className="field-label" htmlFor="product-name">Product name</label>
            <input id="product-name" className="text-field" maxLength={120} required value={productName} onChange={event => setProductName(event.target.value)} placeholder="Enter the product name" />
            <label className="field-label field-spaced" htmlFor="product-category">Product category</label>
            <select id="product-category" className="select-field" value={category} onChange={event => setCategory(event.target.value)}><option value="packaged-snacks">Packaged snacks · demo category</option></select>
            <label className="field-label field-spaced" htmlFor="label-text">Label text to review</label>
            <div className="ocr-controls">
              <span className="field-hint">{capabilities?.ocr?.status === 'AVAILABLE' ? 'Tesseract reads the image on this computer; your package image is not sent to an OCR provider. Correct every result against the image.' : (capabilities?.ocr?.message || 'Local OCR status is not available. You can enter the label text manually.')}</span>
              <button className="button button-secondary button-small" type="button" disabled={capabilities?.ocr?.status !== 'AVAILABLE' || ocrBusy} onClick={extractLabelText}>{ocrBusy ? 'Reading label…' : 'Extract text locally'}</button>
            </div>
            <textarea id="label-text" className="text-field text-area" required maxLength={30000} value={labelText} onChange={event => setLabelText(event.target.value)} placeholder={'Product name\nIngredients: …\nNutrition information: …\nNet quantity: …\nManufactured by: …'} />
            <label className="field-label field-spaced" htmlFor="approved-facts">Approved product facts <span className="micro">OPTIONAL</span></label>
            <span className="field-hint">Add brand-provided facts to compare with the label. Facts are not independently verified.</span>
            <textarea id="approved-facts" className="text-field text-area facts-area" maxLength={30000} value={approvedFacts} onChange={event => setApprovedFacts(event.target.value)} placeholder="Add approved product facts to compare with the label…" />
            <div className="notice field-spaced"><span className="notice-icon">ⓘ</span><span><strong>Human review required.</strong> Barcode results are community-contributed; OCR runs locally. The demo checklist is not an official or verified FSSAI rules check. If you request optional Azure OpenAI suggestions, label text and supplied facts are sent to Azure only after you consent.</span></div>
            <div className="scan-actions"><span className="micro">Checklist demo-v0.1 · sources not verified</span><button className="button button-primary" disabled={busy}>{busy ? 'Creating review…' : 'Run review prompts →'}</button></div>
          </>}
        </form>
        <aside>
          <section className="panel reference-card"><h3>Sample review prompts</h3><p>Examples can help a reviewer spot details to verify; they are not legal requirements.</p><div className="guide-list">{(rules.length ? rules : checklistExamples).map(rule => <div className="guide-row" key={rule.code}><span className="guide-check">{rule.icon || '✓'}</span><div><strong>{rule.title}</strong><p>{rule.prompt}</p></div></div>)}</div><a className="reference-link" href="https://www.fssai.gov.in/docs/food-law/regulations/Comp_Labelling%20Display_Version%20VIII_09_09_2025.pdf" target="_blank" rel="noreferrer">FSSAI labelling compendium · source pending team review ↗</a><a className="reference-link" href="https://www.fssai.gov.in/food-law/regulations/amendments/labelling-display" target="_blank" rel="noreferrer">Review later amendments ↗</a></section>
          <div className="notice notice-spaced"><span className="notice-icon">!</span><span>The team must verify current official materials for the selected category before using regulatory rules.</span></div>
        </aside>
      </div>
      {selectedReview && <ReviewPanel review={selectedReview} onDecision={decideReview} reason={decisionReason} onReason={setDecisionReason} busy={busy} capabilities={capabilities} aiConsent={aiConsent} onAiConsent={setAiConsent} onAiSuggestion={requestAiSuggestion} aiBusy={aiBusy} aiSuggestion={aiSuggestion} />}
    </>
  );

  const renderHistory = () => (
    <>
      <div className="page-intro"><div><div className="eyebrow">Workspace records</div><h1>Review history</h1><p>Review results, source versions, and reviewer decisions stored by the API.</p></div><button className="button button-primary" type="button" onClick={() => { setSelectedReview(null); setUpload(null); navigate('scan'); }}>New review +</button></div>
      {backendError && <div className="notice backend-notice"><span className="notice-icon">!</span><span>{backendError}</span></div>}
      <section className="panel">
        <div className="filter-bar"><span className="micro">{reviews.length} stored review{reviews.length === 1 ? '' : 's'} · local workspace</span><div className="filter-group" role="group" aria-label="Filter reviews">{[['all', 'All reviews'], ['needs-review', 'Needs review'], ['reviewed', 'Reviewed']].map(([value, label]) => <button key={value} className={`filter-chip ${reviewFilter === value ? 'active' : ''}`} type="button" onClick={() => setReviewFilter(value)}>{label}</button>)}</div></div>
        {filteredReviews.length ? <div className="table-wrap"><table className="review-table"><thead><tr><th>Product / label</th><th>Category</th><th>Created</th><th>Status</th><th>Checklist</th></tr></thead><tbody>{filteredReviews.map(review => <tr key={review.id} className="review-table-row" onClick={() => openReview(review)} tabIndex={0} onKeyDown={event => { if (event.key === 'Enter') openReview(review); }}><td><span className="table-product"><span className="product-art real-product"><ProductArtwork /><img className="activity-thumb-image" src={`/api/reviews/${encodeURIComponent(review.id)}/image`} alt="" loading="lazy" /></span>{review.productName}</span></td><td>{review.category}</td><td>{dateLabel(review.createdAt)}</td><td><span className={`row-status ${review.status === 'APPROVED' ? 'done' : ''}`}>{statusLabel(review.status)}</span></td><td>{review.checklistVersion}</td></tr>)}</tbody></table></div> : <div className="empty-state"><strong>No reviews yet</strong>Upload a package image to create the first review.</div>}
      </section>
      {selectedReview && <ReviewPanel review={selectedReview} onDecision={decideReview} reason={decisionReason} onReason={setDecisionReason} busy={busy} capabilities={capabilities} aiConsent={aiConsent} onAiConsent={setAiConsent} onAiSuggestion={requestAiSuggestion} aiBusy={aiBusy} aiSuggestion={aiSuggestion} />}
      <section className="panel review-detail"><h3>Source and service note</h3><p>Checklist prompts are versioned but have not been verified against current official materials. OCR runs locally when Tesseract is installed; optional Azure OpenAI suggestions require separate configuration.</p></section>
    </>
  );

  const renderRules = () => (
    <>
      <div className="page-intro"><div><div className="eyebrow">Review guide · Example only</div><h1>Checklist prompts</h1><p>Versioned examples for a reviewer-led first pass on a grocery package label.</p></div><span className="status-pill"><i /> SOURCE VALIDATION PENDING</span></div>
      <div className="notice field-spaced"><span className="notice-icon">ⓘ</span><span><strong>Not an official FSSAI checklist.</strong> The team must select and verify current official materials for the chosen category. Do not rely on demo prompts as legal requirements.</span></div>
      <div className="rule-grid">{(rules.length ? rules : checklistExamples).map((rule, index) => <article className="panel rule-card" key={rule.code}><div className="rule-card-heading"><span className="rule-symbol">{rule.icon || '✓'}</span><div><span className="micro">{rule.version || `DEMO ${String(index + 1).padStart(2, '0')}`}</span><h3>{rule.title}</h3></div></div><p>{rule.prompt}</p><span className="status-pill pending"><i /> {rule.sourceStatus || 'Verify source & category'}</span></article>)}</div>
      <section className="panel rule-footer"><strong>Human review is not optional.</strong><p>HUNTER is an assistive pre-check, not an FSSAI certification or legal opinion. Review the current official guidance for the chosen category and have an authorized person record the decision.</p><a className="reference-link" href="https://www.fssai.gov.in/" target="_blank" rel="noreferrer">Open the FSSAI website ↗</a></section>
    </>
  );

  const renderInsights = () => (
    <>
      <div className="page-intro"><div><div className="eyebrow">Workspace overview</div><h1>Review insights</h1><p>Summary of records saved in the current workspace.</p></div><span className="demo-pill"><i /> MEASURE REAL RESULTS</span></div>
      <div className="insights-grid"><section className="panel summary-card"><h3>Review status</h3>{[['Awaiting human review', pendingCount], ['Approved by reviewer', reviews.filter(review => review.status === 'APPROVED').length], ['Needs changes', reviews.filter(review => review.status === 'NEEDS_CHANGES').length]].map(([label, count]) => <div className="summary-number" key={label}><strong>{count}</strong><span>{label}</span></div>)}<p className="summary-note">These are actual local workspace records. Do not interpret review outcomes as regulatory compliance results.</p></section>
        <section className="panel summary-card"><h3>Measurement policy</h3><div className="summary-number"><strong>{reviews.length}</strong><span>Stored review records</span></div><p className="summary-note">Measure task completion time and reviewer usefulness only after testing with permitted label samples. Report actual results, not estimates.</p></section></div>
    </>
  );

  const renderSettings = () => (
    <>
      <div className="page-intro"><div><div className="eyebrow">Workspace preferences</div><h1>Settings</h1><p>Data storage, provider configuration, and reviewer details.</p></div></div>
      <div className="settings-grid"><section className="panel settings-card"><h3>Privacy &amp; services</h3>
        <SettingRow title="Image storage" detail="Backend-controlled local file storage. Reviews and images are restricted to their signed-in owner." badge="OWNER ONLY" />
        <SettingRow title="Text extraction (OCR)" detail={capabilities?.ocr?.message || 'Local Tesseract OCR status is not available until the API starts.'} badge={capabilities?.ocr?.status || 'UNKNOWN'} />
        <SettingRow title="Optional AI explanations" detail={capabilities?.ai === 'CONFIGURED' ? 'Azure OpenAI is configured. Label text and facts are sent only after the reviewer opts in.' : 'Azure OpenAI needs an endpoint, key, and deployment name in the API environment.'} badge={capabilities?.ai || 'UNKNOWN'} />
        <SettingRow title="External data transfer" detail="When you choose Find product, only the barcode is sent to Open Food Facts. OCR remains local. Review text is sent to Azure OpenAI only after separate consent and request." badge="BARCODE · ON REQUEST" />
        <SettingRow title="Official checklist source" detail="Current official FSSAI material must be selected and reviewed by the team." badge="PENDING" />
      </section><section className="panel settings-card"><h3>Your account</h3><SettingRow title={user.displayName} detail={user.username} badge={user.role} /><SettingRow title="Workspace" detail={user.workspaceName} badge="PRIVATE" /><SettingRow title="Deployment boundary" detail="Use HTTPS in production and set SESSION_COOKIE_SECURE=true. The app still needs a production database, backups, and operational monitoring before public launch." badge="SECURE DEPLOYMENT REQUIRED" /></section></div>
    </>
  );

  const renderHelp = () => (
    <>
      <div className="page-intro"><div><div className="eyebrow">About this implementation</div><h1>How HUNTER works</h1><p>React and Vite interface · Spring Boot API · relational storage.</p></div></div>
      <div className="help-grid">
        {[
          ['01', 'Set the review context', 'Choose the MVP grocery category, product name, and approved product facts.'],
          ['02', 'Scan barcode and capture label', 'A requested Open Food Facts lookup uses only the barcode. The Spring Boot API validates the package image before storing it locally.'],
          ['03', 'Extract and verify text', 'Optional Tesseract OCR runs on this computer; the reviewer corrects every line against the uploaded package.'],
          ['04', 'Run deterministic checks', 'Versioned prompts compare field cues and on-pack claims against approved facts. They do not determine legality.'],
          ['05', 'Record reviewer decisions', 'Your signed-in account is recorded as reviewer along with the decision, reason, and checklist version.'],
          ['06', 'Record any decision', 'A human reviewer records the outcome. It is not a legal or compliance certification.']
        ].map(([number, title, description]) => <article className="panel help-card" key={number}><span className="help-icon">{number}</span><h3>{title}</h3><p>{description}</p></article>)}
      </div>
      <section className="panel rule-footer"><strong>Source and service policy</strong><p>Use current FSSAI regulations and amendments selected and reviewed by the team. These sample prompts are not an official checklist. Tesseract OCR runs locally. Optional Azure OpenAI suggestions require local configuration and an explicit reviewer request.</p><a className="reference-link" href="https://www.fssai.gov.in/docs/food-law/regulations/Comp_Labelling%20Display_Version%20VIII_09_09_2025.pdf" target="_blank" rel="noreferrer">FSSAI Labelling &amp; Display compendium (9 Sep 2025) ↗</a><a className="reference-link" href="https://www.fssai.gov.in/food-law/regulations/amendments/labelling-display" target="_blank" rel="noreferrer">Check FSSAI amendments ↗</a></section>
    </>
  );

  if (!authChecked) {
    return <main className="auth-page"><p className="auth-loading">Checking your secure session…</p></main>;
  }
  if (!user) {
    return <AuthScreen mode={authMode} onModeChange={mode => { setAuthMode(mode); setAuthError(''); }} onSubmit={handleAuthSubmit} busy={authBusy} error={authError || backendError} />;
  }

  const content = {
    dashboard: renderDashboard,
    scan: renderScan,
    history: renderHistory,
    rules: renderRules,
    insights: renderInsights,
    settings: renderSettings,
    help: renderHelp
  }[page]();

  return (
    <div className="app-shell">
      <aside className={`sidebar ${mobileOpen ? 'mobile-open' : ''}`}>
        <button className="brand brand-button" type="button" onClick={() => navigate('dashboard')} aria-label="HUNTER dashboard"><Logo /><span className="brand-copy"><strong>HUNTER</strong><small>LABEL REVIEW WORKSPACE</small></span></button>
        <div className="workspace-label">WORKSPACE</div>{nav}
        <div className="workspace-label nav-secondary-label">PREFERENCES</div>
        <nav className="primary-nav" aria-label="Preferences">{Object.entries(icon).slice(5).map(([key, glyph]) => <button key={key} className={`nav-link ${page === key ? 'active' : ''}`} type="button" onClick={() => navigate(key)} aria-current={page === key ? 'page' : undefined}><span className="nav-icon" aria-hidden="true">{glyph}</span><span>{pageNames[key]}</span></button>)}</nav>
        <div className="sidebar-spacer" />
        <div className="privacy-card"><span className="privacy-icon" aria-hidden="true">⌁</span><strong>Local records, reviewer-controlled services.</strong><p>OCR stays on this device. Product lookup sends only the barcode to Open Food Facts.</p><span className="privacy-status"><i /> LOCAL OCR</span></div>
        <div className="sidebar-footer"><span className="footer-dot" /> HUNTER <span>·</span> DEMO 0.1</div>
      </aside>
      {mobileOpen && <button className="sidebar-backdrop" type="button" aria-label="Close navigation" onClick={() => setMobileOpen(false)} />}
      <main className="main-shell">
        <header className="topbar">
          <button className="mobile-menu icon-button" type="button" aria-label="Open navigation" onClick={() => setMobileOpen(open => !open)}>☰</button>
          <label className="search-box"><span aria-hidden="true">⌕</span><input id="global-search" type="search" placeholder="Search any product…" value={query} onChange={event => setQuery(event.target.value)} aria-label="Search reviews and products" /><kbd>⌘ K</kbd></label>
          <button className="icon-button notification-button" type="button" aria-label="Notifications" onClick={() => notify('You’re all caught up. Notifications are not connected in this demo.')}><span aria-hidden="true">♧</span><i /></button>
          <div className="profile-wrap"><button className="profile-button" type="button" onClick={() => setProfileOpen(open => !open)} aria-expanded={profileOpen}><span className="avatar">{user.displayName.slice(0, 1).toUpperCase()}</span><span className="profile-name">{user.displayName}</span><span className="profile-chevron">⌄</span></button>{profileOpen && <div className="profile-menu"><span className="profile-menu-label">{user.username}</span><button type="button" onClick={() => navigate('settings')}>Account settings</button><button type="button" onClick={() => navigate('help')}>Help &amp; support</button><button type="button" onClick={handleLogout}>Sign out</button></div>}</div>
        </header>
        <section className="page-view" key={page} aria-live="polite">{content}</section>
      </main>
      {notice && <div className="toast-region" role="status">{notice}</div>}
      <input ref={cameraInput} type="file" accept="image/*" capture="environment" hidden onChange={event => { chooseImage(event.target.files?.[0], true); event.target.value = ''; }} />
    </div>
  );
}

function statusLabel(status = '') {
  return ({
    NEEDS_HUMAN_REVIEW: 'Needs review',
    APPROVED: 'Approved by reviewer',
    NEEDS_CHANGES: 'Needs changes',
    IN_PROGRESS: 'In progress'
  })[status] || status.replaceAll('_', ' ').toLowerCase();
}

function SettingRow({ title, detail, badge }) {
  return <div className="settings-row"><div><strong>{title}</strong><p>{detail}</p></div><span className="status-pill">{badge}</span></div>;
}

function ReviewPanel({ review, onDecision, reason, onReason, busy, capabilities, aiConsent, onAiConsent, onAiSuggestion, aiBusy, aiSuggestion }) {
  return (
    <section className="panel review-detail" id="review-findings">
      <div className="result-header"><div><h3>{review.productName}</h3><p>{review.category} · {review.checklistVersion} · {dateLabel(review.createdAt)}</p></div><span className="status-pill"><i /> {statusLabel(review.status)}</span></div>
      <div className="review-image-wrap"><img src={`/api/reviews/${encodeURIComponent(review.id)}/image`} alt={`Label image for ${review.productName}`} /></div>
      <div className="review-facts"><span><strong>Label text</strong>{review.labelText}</span>{review.approvedFacts && <span><strong>Approved facts supplied</strong>{review.approvedFacts}</span>}</div>
      <div className="result-list">{review.findings?.map(finding => <div className="result-item" key={finding.id || finding.ruleCode}><span className={`result-icon ${finding.attentionRequired ? '' : 'found'}`}>{finding.attentionRequired ? '!' : '✓'}</span><div><strong>{finding.title}</strong><p>{finding.detail}</p></div><span className={`result-badge ${finding.attentionRequired ? '' : 'found'}`}>{finding.resultLabel}</span></div>)}</div>
      <div className="notice notice-spaced"><span className="notice-icon">ⓘ</span><span>Text matching is an assistive pre-check, not a compliance determination. Verify the package, product facts, and current official sources.</span></div>
      <section className="ai-review-suggestions">
        <div><strong>Optional reviewer wording suggestions</strong><p>Azure OpenAI may help phrase follow-up questions. It cannot verify rules or decide compliance.</p></div>
        <label className="cloud-consent"><input type="checkbox" checked={aiConsent} onChange={event => onAiConsent(event.target.checked)} /><span>I agree to send this label text, supplied facts, and findings to Azure OpenAI.</span></label>
        <button className="button button-secondary button-small" type="button" disabled={!aiConsent || capabilities?.ai !== 'CONFIGURED' || aiBusy} onClick={() => onAiSuggestion(review)}>{aiBusy ? 'Drafting suggestions…' : 'Draft reviewer suggestions'}</button>
        {capabilities?.ai !== 'CONFIGURED' && <p className="field-hint">Azure OpenAI needs endpoint, key, and deployment settings in the API environment before use.</p>}
        {aiSuggestion && <div className="ai-suggestion-result" aria-live="polite">{aiSuggestion}</div>}
      </section>
      <div className="field-spaced"><label className="field-label" htmlFor="decision-reason">Reviewer note <span className="micro">OPTIONAL</span></label><textarea className="text-field" id="decision-reason" maxLength={2000} value={reason} onChange={event => onReason(event.target.value)} placeholder="Record the reason for this review decision…" /></div>
      <div className="scan-actions decision-actions"><span className="micro">Record a human reviewer decision</span><span><button className="button button-quiet button-small" type="button" disabled={busy} onClick={() => onDecision('NEEDS_CHANGES')}>Needs changes</button> <button className="button button-primary button-small" type="button" disabled={busy} onClick={() => onDecision('APPROVED')}>Reviewer approved</button></span></div>
      {review.decisions?.length > 0 && <div className="decision-history"><h4>Decision history</h4>{review.decisions.map(decision => <div key={decision.id} className="decision-entry"><strong>{statusLabel(decision.decision)}</strong><span>{decision.reviewerName} · {dateLabel(decision.createdAt)}</span>{decision.reason && <p>{decision.reason}</p>}</div>)}</div>}
    </section>
  );
}

createRoot(document.getElementById('root')).render(<App />);
