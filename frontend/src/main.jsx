import { StrictMode, useState } from 'react'
import { createRoot } from 'react-dom/client'
import './styles.css'

const labels = { HIGH: 'Высокий риск', MEDIUM: 'Требует внимания', LOW: 'Низкий риск' }

function App() {
  const [file, setFile] = useState(null)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function submit(event) {
    event.preventDefault()
    if (!file) return setError('Сначала выберите документ.')
    setLoading(true); setError(''); setResult(null)
    const data = new FormData(); data.append('file', file)
    try {
      const response = await fetch('/api/documents/analyze', { method: 'POST', body: data })
      const payload = await response.json()
      if (!response.ok) throw new Error(payload.message || 'Не удалось проанализировать файл.')
      setResult(payload)
    } catch (e) { setError(e.message) } finally { setLoading(false) }
  }

  return <main>
    <section className="hero"><span className="badge">⚖ КАРМАННЫЙ ЮРИСТ · НА СВЯЗИ</span><p className="eyebrow">Ваш документ. Ваши права. Ваш ход.</p><h1>Проверим<br/>БУМАГИ.</h1><p>Загрузите договор — сервис выделит типовые юридические риски и скажет, куда смотреть внимательнее.</p></section>
    <section className="panel">
      <form onSubmit={submit}>
        <div className="hotline">БЫСТРАЯ ПРОВЕРКА · БЕЗ МЕЛКОГО ШРИФТА*</div><label className="dropzone"><input type="file" accept=".pdf,.docx,.txt" onChange={e => setFile(e.target.files?.[0] || null)} /><span className="file-icon">↑</span><strong>{file ? file.name : 'Загрузите ваш документ'}</strong><small>{file ? `${Math.ceil(file.size / 1024)} КБ` : 'PDF, DOCX или TXT · до 10 МБ'}</small></label>
        {error && <p className="error">{error}</p>}<button disabled={loading}>{loading ? 'Анализируем…' : 'Проверить документ'}</button>
      </form>
      {result && <article className="result"><div className={`risk ${result.riskLevel}`}><span>Общая оценка</span><strong>{labels[result.riskLevel]}</strong></div><h2>Заключение</h2><p>{result.summary}</p>
        {result.strengths?.length ? <div className="strengths"><h2>Сильные стороны</h2><ul>{result.strengths.map((strength, i) => <li key={i}>{strength}</li>)}</ul></div> : null}
        {result.findings.length ? <div className="findings">{result.findings.map((f, i) => <div className="finding" key={i}><span className={`dot ${f.severity}`}></span><div><h3>{f.title}</h3><p>{f.description}</p><b>Что сделать:</b> {f.recommendation}</div></div>)}</div> : null}
        <footer>* {result.disclaimer}</footer></article>}
    </section>
  </main>
}

createRoot(document.getElementById('root')).render(<StrictMode><App /></StrictMode>)
