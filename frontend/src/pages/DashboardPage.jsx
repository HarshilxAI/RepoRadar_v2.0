import { ArcElement, Chart as ChartJS, Legend, Tooltip } from 'chart.js'
import { Doughnut } from 'react-chartjs-2'
import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { AlertTriangle, BarChart3, CalendarDays, CheckCircle2, ChevronRight, Download, ExternalLink, FolderTree, GitBranch, GitFork, GitPullRequest, Info, Lightbulb, PackageCheck, Star, Tag, Users, Eye, XCircle } from 'lucide-react'
import Header from '../components/Header'
import LoadingExperience from '../components/LoadingExperience'
import ErrorState from '../components/ErrorState'
import {
  analyzeRepository,
  generateAiInsights,
} from '../services/api'
import { formatDate, formatNumber } from '../utils/repository'
import { exportReport } from '../utils/report'

ChartJS.register(ArcElement, Tooltip, Legend)

const scoreTone = (score) => score >= 90 ? 'text-green-700 bg-green-600' : score >= 70 ? 'text-teal bg-teal' : score >= 50 ? 'text-amber bg-amber' : 'text-red-700 bg-red-600'
const metrics = [
  ['Stars', 'stars', Star], ['Forks', 'forks', GitFork], ['Issues', 'issues', Info], ['Watchers', 'watchers', Eye], ['Contributors', 'contributors', Users], ['Repository size', 'sizeKb', PackageCheck], ['Open pull requests', 'openPullRequests', GitPullRequest], ['Latest release', 'latestRelease', Tag], ['Latest commit', 'latestCommit', GitBranch],
]

function Section({ icon: Icon, eyebrow, title, children, action }) {
  return <section className="mt-8" aria-labelledby={title.replaceAll(' ', '-').toLowerCase()}><div className="mb-4 flex items-end justify-between gap-4"><div><p className="eyebrow">{eyebrow}</p><h2 id={title.replaceAll(' ', '-').toLowerCase()} className="section-title mt-1">{title}</h2></div>{action}</div>{children}</section>
}

export default function DashboardPage() {
  const [searchParams] = useSearchParams()
  const repositoryUrl = searchParams.get('repository') || ''
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [aiInsights, setAiInsights] = useState(null)
const [aiInsightsError, setAiInsightsError] = useState('')
const [aiInsightsLoading, setAiInsightsLoading] = useState(false)
  const load = useCallback(async () => {
    setLoading(true); setError(''); setData(null)
    const start = Date.now()
    try { const result = await analyzeRepository(repositoryUrl); await new Promise((resolve) => setTimeout(resolve, Math.max(0, 2200 - (Date.now() - start)))); setData(result) }
    catch (requestError) { setError(requestError.message); }
    finally { setLoading(false) }
  }, [repositoryUrl])
  
const handleGenerateAiInsights = async () => {
  setAiInsightsLoading(true)
  setAiInsightsError('')
  setAiInsights(null)

  try {
    const result = await generateAiInsights(repositoryUrl)
    setAiInsights(result)
  } catch (requestError) {
    setAiInsightsError(requestError.message)
  } finally {
    setAiInsightsLoading(false)
  }
}

  useEffect(() => { load() }, [load])
  if (loading) return <><Header dashboard /><LoadingExperience /></>
  if (error) return <><Header dashboard /><ErrorState message={error} onRetry={load} /></>
  return <><Header dashboard /><main className="mx-auto max-w-7xl px-5 py-8 lg:px-8 lg:py-10">
    <RepositoryHeader repository={data.repository} />
    <ExecutiveSummary data={data} />
    <HealthOverview health={data.health} />
    <Section eyebrow="At a glance" title="Quick metrics"><div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{metrics.map(([label, field, Icon]) => <MetricCard key={label} label={label} value={data.metrics[field]} icon={Icon} />)}</div></Section>
    <div className="grid gap-8 xl:grid-cols-5"><div className="xl:col-span-3"><Languages languages={data.languages} /></div><div className="xl:col-span-2"><Activity activity={data.activity} /></div></div>
    <Section eyebrow="Documentation quality" title="README analysis"><Readme readme={data.readme} /></Section>
    <div className="grid gap-8 xl:grid-cols-2"><Technologies technologies={data.technologies} /><Structure structure={data.structure} /></div>
    <div className="grid gap-8 xl:grid-cols-2"><Community community={data.community} /><Snapshot snapshot={data.snapshot} /></div>
    <Section eyebrow="Focused next actions" title="Suggestions" action={<button onClick={() => exportReport(data)} className="hidden items-center gap-2 rounded-lg border border-slate-300 px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-white sm:inline-flex"><Download size={16} />Export report</button>}><Suggestions suggestions={data.suggestions} /></Section>
    <section className="mt-8 rounded-2xl bg-slate-900 p-6 text-white sm:flex sm:items-center sm:justify-between"><div><p className="text-sm font-semibold text-slate-300">Ready to share the findings?</p><h2 className="mt-1 text-xl font-bold">Download a professional repository report.</h2></div><button onClick={() => exportReport(data)} className="mt-4 inline-flex items-center gap-2 rounded-lg bg-coral px-4 py-2.5 text-sm font-bold text-white hover:bg-[#d95446] sm:mt-0"><Download size={17} />Download PDF</button></section>
  </main></>
}

function ExecutiveSummary({ data }) {
  const majorIssues = data.suggestions.filter((suggestion) => suggestion.priority === 'High')
  const issues = majorIssues.length ? majorIssues : data.snapshot.needsImprovement
  const nextAction = data.suggestions[0]
  return <section className="mt-8" aria-labelledby="quick-read"><div className="mb-4"><p className="eyebrow">Quick read</p><h2 id="quick-read" className="section-title mt-1">What deserves attention first</h2></div><div className="grid gap-4 lg:grid-cols-3">
    <article className="surface border-l-4 border-l-coral p-5"><div className="flex items-start justify-between gap-3"><div><p className="text-xs font-bold uppercase tracking-wider text-slate-500">Overall score</p><p className="mt-2 text-4xl font-extrabold text-slate-950 dark:text-white">{data.health.score}<span className="ml-1 text-base font-semibold text-slate-400">/100</span></p></div><span className="rounded-full bg-coral/10 px-2.5 py-1 text-xs font-bold text-coral">{data.health.grade}</span></div><ProgressBar value={data.health.score} tone="coral" /><p className="mt-3 text-sm text-slate-600 dark:text-slate-300">{data.health.label}</p></article>
    <article className="surface p-5"><div className="flex items-center gap-2"><span className="grid h-8 w-8 place-items-center rounded-lg bg-amber-50 text-amber-700 dark:bg-amber-400/10 dark:text-amber-300"><AlertTriangle size={17} /></span><p className="text-xs font-bold uppercase tracking-wider text-slate-500">Major issues</p></div>{issues.length ? <ul className="mt-4 space-y-2">{issues.slice(0, 3).map((issue) => <li key={typeof issue === 'string' ? issue : issue.title} className="flex gap-2 text-sm font-medium text-slate-700 dark:text-slate-200"><ChevronRight size={15} className="mt-0.5 shrink-0 text-amber-600" />{typeof issue === 'string' ? issue : issue.title}</li>)}</ul> : <p className="mt-4 text-sm font-medium text-teal">No major issues found.</p>}</article>
    <article className="surface p-5"><p className="text-xs font-bold uppercase tracking-wider text-slate-500">Recommended next step</p><h3 className="mt-3 font-bold text-slate-900 dark:text-white">{nextAction?.title || 'Keep the quality bar high'}</h3><p className="mt-2 line-clamp-3 text-sm leading-6 text-slate-600 dark:text-slate-300">{nextAction?.description || 'Maintain the documentation, release cadence, and automated checks already present.'}</p><a href="#suggestions" className="mt-4 inline-flex items-center gap-1 text-sm font-bold text-teal hover:underline">View recommendations <ChevronRight size={15} /></a></article>
  </div></section>
}

function RepositoryHeader({ repository }) {
  return <section className="surface overflow-hidden"><div className="flex flex-col gap-6 p-6 sm:p-8 lg:flex-row lg:items-start lg:justify-between"><div className="flex min-w-0 gap-4"><img className="h-14 w-14 shrink-0 rounded-2xl border border-slate-200 bg-slate-100" src={repository.ownerAvatarUrl} alt={`${repository.owner} avatar`} /><div className="min-w-0"><p className="eyebrow">Public GitHub repository</p><h1 className="mt-1 truncate text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">{repository.fullName}</h1><p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">{repository.description || 'No repository description provided.'}</p></div></div><a className="inline-flex shrink-0 items-center justify-center gap-2 rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white hover:bg-slate-700" href={repository.htmlUrl} target="_blank" rel="noreferrer">Open in GitHub <ExternalLink size={16} /></a></div>
    <div className="grid border-t border-slate-200 bg-slate-50/70 text-sm sm:grid-cols-2 lg:grid-cols-5">{[['Default branch', repository.defaultBranch], ['License', repository.license], ['Created', formatDate(repository.createdAt)], ['Updated', formatDate(repository.updatedAt)], ['Visibility', repository.visibility]].map(([label, value]) => <div key={label} className="border-b border-slate-200 px-5 py-4 lg:border-b-0 lg:border-r last:border-0"><p className="text-xs font-medium text-slate-500">{label}</p><p className="mt-1 truncate font-semibold text-slate-800">{value}</p></div>)}</div>
    <div className="grid gap-2 border-t border-slate-200 bg-white px-5 py-4 text-sm sm:grid-cols-2">
      <div>
        <p className="text-xs font-medium text-slate-500">Repository URL</p>
        <a className="mt-1 block truncate font-semibold text-teal hover:underline" href={repository.htmlUrl} target="_blank" rel="noreferrer">{repository.htmlUrl}</a>
      </div>
      <div>
        <p className="text-xs font-medium text-slate-500">Clone URL</p>
        <p className="mt-1 truncate font-semibold text-slate-800" title={repository.cloneUrl}>{repository.cloneUrl || 'Not available'}</p>
      </div>
    </div>
  </section>
}

function HealthOverview({ health }) {
  const [textTone, fillTone] = scoreTone(health.score).split(' ')
  return <Section eyebrow="RepoRadar health score" title="Repository health"><div className="surface p-6 sm:p-8"><div className="grid gap-8 lg:grid-cols-[220px_1fr]"><div className="grid place-items-center"><div><div className="grid h-44 w-44 place-items-center rounded-full" style={{ background: `conic-gradient(${health.score >= 90 ? '#16a34a' : health.score >= 70 ? '#0f766e' : health.score >= 50 ? '#d97706' : '#dc2626'} ${health.score * 3.6}deg, #e2e8f0 0deg)` }}><div className="grid h-36 w-36 place-items-center rounded-full bg-white text-center"><strong className="text-4xl font-extrabold text-slate-900">{health.score}</strong><span className="text-xs font-medium text-slate-500">out of 100</span></div></div><ProgressBar value={health.score} tone="teal" /></div></div><div><span className={`inline-flex rounded-full px-3 py-1 text-sm font-bold ${textTone} bg-current/10`}>{health.label}</span><p className="mt-3 max-w-2xl muted">{health.explanation}</p><div className="mt-6 grid gap-4 sm:grid-cols-2">{health.breakdown.map((category) => <div key={category.name}><div className="flex justify-between gap-3 text-sm"><span className="font-semibold text-slate-700">{category.name}</span><span className="font-bold text-slate-900">{category.score}</span></div><ProgressBar value={category.score} tone={fillTone.includes('green') ? 'green' : fillTone.includes('amber') ? 'amber' : fillTone.includes('red') ? 'red' : 'teal'} /><p className="mt-1 text-xs text-slate-500">{category.description}</p></div>)}</div></div></div></div></Section>
}

function ProgressBar({ value, tone = 'teal' }) {
  const tones = { teal: 'bg-teal', coral: 'bg-coral', green: 'bg-green-600', amber: 'bg-amber-500', red: 'bg-red-600' }
  return <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800" role="progressbar" aria-valuenow={value} aria-valuemin="0" aria-valuemax="100" aria-label={`${value} out of 100`}><div className={`h-full rounded-full transition-all duration-700 ${tones[tone] || tones.teal}`} style={{ width: `${Math.max(0, Math.min(100, value))}%` }} /></div>
}

function MetricCard({ label, value, icon: Icon }) { const display = typeof value === 'number' ? formatNumber(value) : value; return <article className="surface min-w-0 p-4"><div className="flex items-start justify-between gap-3"><p className="text-sm font-medium text-slate-500">{label}</p><Icon size={18} className="shrink-0 text-teal" /></div><p className="mt-3 truncate text-xl font-bold text-slate-900" title={String(display)}>{display}</p></article> }

function Languages({ languages }) { const chart = { labels: languages.map((item) => item.name), datasets: [{ data: languages.map((item) => item.bytes), backgroundColor: languages.map((item) => item.color), borderWidth: 0 }] }; return <Section eyebrow="Code composition" title="Language analysis"><div className="surface grid items-center gap-6 p-6 sm:grid-cols-2"><div className="mx-auto h-56 w-56">{languages.length ? <Doughnut data={chart} options={{ cutout: '68%', plugins: { legend: { display: false }, tooltip: { callbacks: { label: (context) => `${context.label}: ${languages[context.dataIndex].percentage}%` } } } }} /> : <div className="grid h-full place-items-center rounded-full border-8 border-slate-100 text-center text-sm text-slate-500">No language data</div>}</div><div className="space-y-3">{languages.map((language) => <div key={language.name} className="flex items-center justify-between gap-3 text-sm"><span className="flex min-w-0 items-center gap-2"><i className="h-3 w-3 shrink-0 rounded-full" style={{ backgroundColor: language.color }} /><span className="truncate font-medium text-slate-700">{language.name}</span></span><span className="shrink-0 text-slate-500">{language.percentage}% · {formatNumber(language.bytes)} B</span></div>)}</div></div></Section> }

function Activity({ activity }) { return <Section eyebrow="Maintenance signals" title="Activity analysis"><div className="surface p-6"><div className="space-y-5">{[[CalendarDays, 'Latest commit', activity.latestCommit], [BarChart3, 'Commit cadence', activity.commitFrequency], [GitBranch, 'Branches', activity.branches], [Tag, 'Releases', activity.releases], [Users, 'Contributors', activity.contributors]].map(([Icon, label, value]) => <div key={label} className="flex items-start gap-3"><Icon size={18} className="mt-0.5 shrink-0 text-teal" /><div><p className="text-xs font-medium text-slate-500">{label}</p><p className="mt-0.5 text-sm font-semibold text-slate-800">{value}</p></div></div>)}</div>{activity.inactive && <p className="mt-6 rounded-lg bg-amber-50 p-3 text-sm leading-5 text-amber-800">This repository has had limited activity in the last year. That can be appropriate for stable projects, but it is worth clarifying maintenance expectations.</p>}</div></Section> }

function Readme({ readme }) { return <div className="surface p-6 sm:p-8"><div className="grid gap-7 lg:grid-cols-[150px_1fr]"><div className="text-center"><p className="text-5xl font-extrabold text-teal">{readme.score}</p><p className="mt-1 text-sm font-medium text-slate-500">README score</p><ProgressBar value={readme.score} tone="teal" /></div><div><p className="muted">{readme.summary}</p><div className="mt-5 grid gap-x-6 gap-y-3 sm:grid-cols-2 lg:grid-cols-3">{readme.checklist.map((item) => <div key={item.label} className="flex items-center gap-2 text-sm"><span className={item.present ? 'text-green-600' : 'text-slate-300'}>{item.present ? <CheckCircle2 size={17} /> : <XCircle size={17} />}</span><span className={item.present ? 'text-slate-700' : 'text-slate-500'}>{item.label}</span></div>)}</div></div></div><div className="mt-7 grid gap-5 border-t border-slate-100 pt-6 md:grid-cols-3"><InsightList title="Strengths" items={readme.strengths} /><InsightList title="Gaps" items={readme.weaknesses} /><InsightList title="Recommendations" items={readme.recommendations} /></div></div> }
function InsightList({ title, items }) { return <div><h3 className="text-sm font-bold text-slate-900">{title}</h3>{items.length ? <ul className="mt-2 space-y-2">{items.map((item) => <li key={item} className="flex gap-2 text-sm leading-5 text-slate-600"><ChevronRight size={15} className="mt-0.5 shrink-0 text-teal" />{item}</li>)}</ul> : <p className="mt-2 text-sm text-slate-500">No notable items.</p>}</div> }

function Technologies({ technologies }) { return <Section eyebrow="Detected from code and configuration" title="Technology detection"><div className="surface p-6"><div className="flex flex-wrap gap-2">{technologies.length ? technologies.map((technology) => <span key={technology.name} title={`${technology.evidence} (${technology.confidence}% confidence)`} className="rounded-lg border border-teal/20 bg-teal/5 px-3 py-2 text-sm font-semibold text-teal">{technology.name}<small className="ml-1 font-normal text-teal/70">{technology.confidence}%</small></span>) : <p className="muted">No known technology signatures were detected from the available repository tree.</p>}</div></div></Section> }
function Structure({ structure }) { return <Section eyebrow="Project layout" title="Structure insights"><div className="surface p-6"><p className="text-3xl font-extrabold text-teal">{structure.score}<span className="text-sm font-medium text-slate-500"> / 100</span></p><ProgressBar value={structure.score} tone="teal" /><div className="mt-4 grid gap-5 sm:grid-cols-2"><InsightList title="Good practices" items={structure.goodPractices} /><InsightList title="Missing or unclear" items={structure.missingFiles} /></div>{structure.topLevelFolders.length > 0 && <div className="mt-5 border-t border-slate-100 pt-4"><p className="text-xs font-bold uppercase tracking-wider text-slate-500">Top-level folders</p><div className="mt-2 flex flex-wrap gap-2">{structure.topLevelFolders.map((folder) => <span key={folder} className="inline-flex items-center gap-1 rounded-md bg-slate-100 px-2 py-1 text-xs font-medium text-slate-600"><FolderTree size={13} />{folder}</span>)}</div></div>}</div></Section> }
function Community({ community }) { return <Section eyebrow="Collaboration signals" title="Community analysis"><div className="surface grid grid-cols-2 gap-5 p-6 sm:grid-cols-4"><CommunityMetric label="Stars" value={community.stars} /><CommunityMetric label="Forks" value={community.forks} /><CommunityMetric label="Watchers" value={community.watchers} /><CommunityMetric label="Contributors" value={community.contributors} /><div className="col-span-2 border-t border-slate-100 pt-4 sm:col-span-4"><p className="text-sm font-semibold text-slate-800">Community score: <span className="text-teal">{community.score}/100</span></p><ProgressBar value={community.score} tone="teal" /><p className="mt-2 text-sm text-slate-500">Engagement contributes to the score without overwhelming code and documentation quality.</p></div></div></Section> }
function CommunityMetric({ label, value }) { return <div><p className="text-xs font-medium text-slate-500">{label}</p><p className="mt-1 text-xl font-bold text-slate-900">{formatNumber(value)}</p></div> }
function Snapshot({ snapshot }) { return <Section eyebrow="Developer snapshot" title="Repository grade"><div className="surface p-6"><div className="flex items-center gap-4"><span className="grid h-16 w-16 place-items-center rounded-2xl bg-coral text-2xl font-extrabold text-white">{snapshot.grade}</span><p className="muted">{snapshot.summary}</p></div><div className="mt-6 grid gap-5 sm:grid-cols-2"><InsightList title="Strengths" items={snapshot.strengths} /><InsightList title="Needs improvement" items={snapshot.needsImprovement} /></div></div></Section> }
function Suggestions({ suggestions }) { return <div id="suggestions" className="grid scroll-mt-24 gap-4 lg:grid-cols-2">{suggestions.map((suggestion) => <article key={suggestion.title} className="surface p-5"><div className="flex items-start justify-between gap-4"><div className="flex gap-3"><span className="mt-0.5 text-coral"><Lightbulb size={20} /></span><div><h3 className="font-bold text-slate-900 dark:text-white">{suggestion.title}</h3><p className="mt-1 text-sm leading-6 text-slate-600 dark:text-slate-300">{suggestion.description}</p></div></div><span className={`rounded-full px-2.5 py-1 text-xs font-bold ${suggestion.priority === 'High' ? 'bg-red-50 text-red-700' : suggestion.priority === 'Medium' ? 'bg-amber-50 text-amber-800' : 'bg-teal/10 text-teal'}`}>{suggestion.priority}</span></div><div className="mt-4 border-t border-slate-100 pt-3 text-xs leading-5 text-slate-500"><p><b className="text-slate-700 dark:text-slate-200">Why:</b> {suggestion.reason}</p><p className="mt-1"><b className="text-slate-700 dark:text-slate-200">Benefit:</b> {suggestion.expectedBenefit}</p></div></article>)}</div> }
