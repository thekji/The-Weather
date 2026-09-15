import {
    MapPin,
    MoreHorizontal,
    Star,
    ThumbsDown,
    ThumbsUp,
    Trash2,
    UserRound,
} from 'lucide-react'
import { useState } from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router-dom'
import { getWeatherArtwork } from '../../weatherArtwork'
import { deletePost, removePostFeedback, setPostFeedback } from '../service/postService'
import {
    weatherAccuracyRatingLabel,
    type CommunityPost,
    type FeedbackType,
} from '../types'

type Props = {
    post: CommunityPost
    currentUserID?: string
    currentUsername?: string
    showLocation?: boolean
    onDeleted: (postID: string) => void
}

function displayedUsername(post: CommunityPost, currentUserID?: string,
    currentUsername?: string): string {
    if (post.username && post.username !== post.userID) return post.username
    if (post.userID === currentUserID && currentUsername) return currentUsername
    return 'Community user'
}

function relativeTime(value: string): string {
    const timestamp = new Date(value).getTime()
    if (!Number.isFinite(timestamp)) return value

    const seconds = Math.max(0, Math.floor((Date.now() - timestamp) / 1000))
    if (seconds < 60) return 'Just now'

    const minutes = Math.floor(seconds / 60)
    if (minutes < 60) return `${minutes} minute${minutes === 1 ? '' : 's'} ago`

    const hours = Math.floor(minutes / 60)
    if (hours < 24) return `${hours} hour${hours === 1 ? '' : 's'} ago`

    const days = Math.floor(hours / 24)
    if (days < 7) return `${days} day${days === 1 ? '' : 's'} ago`

    return new Intl.DateTimeFormat('en', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
    }).format(new Date(timestamp))
}

function recordedTime(value: string): string {
    const rawTime = value.split('T')[1]?.slice(0, 5)
    if (!rawTime) return value

    const [rawHour, minute] = rawTime.split(':')
    const hour = Number(rawHour)
    if (!Number.isInteger(hour) || minute === undefined) return rawTime
    return `${hour % 12 || 12}:${minute} ${hour < 12 ? 'AM' : 'PM'}`
}

function recordedDuringDay(value: string): boolean {
    const hour = Number(value.split('T')[1]?.slice(0, 2))
    return !Number.isFinite(hour) || (hour >= 6 && hour < 18)
}

export function CommunityPostCard({ post, currentUserID, currentUsername, showLocation, onDeleted }: Props) {
    const [error, setError] = useState('')
    const [busy, setBusy] = useState(false)
    const [menuOpen, setMenuOpen] = useState(false)
    const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false)
    const [helpfulCount, setHelpfulCount] = useState(post.helpfulCount)
    const [notHelpfulCount, setNotHelpfulCount] = useState(post.notHelpfulCount)
    const [myFeedback, setMyFeedback] = useState<FeedbackType | null>(post.myFeedback)
    const [feedbackBusy, setFeedbackBusy] = useState<FeedbackType | null>(null)
    const isOwner = currentUserID === post.userID
    const ratingLabel = weatherAccuracyRatingLabel(post.weatherAccuracyRating)
    const imageGridClassName = post.imageUrls.length === 1
        ? 'grid-cols-1'
        : post.imageUrls.length === 2
            ? 'grid-cols-2'
            : 'grid-cols-2 grid-rows-2'

    async function executeRemove() {
        if (busy) return
        setBusy(true)
        setError('')
        setConfirmDeleteOpen(false)
        try {
            await deletePost(post.postID)
            onDeleted(post.postID)
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : 'Delete failed.')
        } finally {
            setBusy(false)
        }
    }

    async function submitFeedback(feedbackType: FeedbackType) {
        if (feedbackBusy || isOwner) return
        setFeedbackBusy(feedbackType)
        setError('')

        try {
            const response = myFeedback === feedbackType
                ? await removePostFeedback(post.postID)
                : await setPostFeedback(post.postID, feedbackType)
            setHelpfulCount(response.helpfulCount)
            setNotHelpfulCount(response.notHelpfulCount)
            setMyFeedback(response.myFeedback)
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : 'Feedback could not be submitted.')
        } finally {
            setFeedbackBusy(null)
        }
    }

    return (
        <article className="rounded-2xl border border-[var(--line)] bg-[var(--surface)] p-4 shadow-sm text-[var(--ink)]">
            <header className="relative flex items-center gap-3">
                <span className="grid size-10 shrink-0 place-items-center rounded-full bg-[var(--active-surface)] text-[var(--active-icon)] font-bold">
                    <UserRound className="size-5" aria-hidden="true" />
                </span>
                <div className="min-w-0">
                    <strong className="block truncate text-sm font-bold text-[var(--ink)]">{displayedUsername(post, currentUserID, currentUsername)}</strong>
                    <time className="mt-0.5 block text-xs text-[var(--muted)] font-medium" dateTime={post.createdAt}>{relativeTime(post.createdAt)}</time>
                </div>

                {isOwner && (
                    <div className="relative ml-auto self-start">
                        <button
                            className="grid size-8 cursor-pointer place-items-center rounded-full border border-transparent bg-transparent text-[var(--muted)] hover:bg-[var(--soft-surface)] hover:text-[var(--ink)] transition-colors"
                            type="button"
                            aria-label="Post actions"
                            aria-expanded={menuOpen}
                            onClick={() => setMenuOpen((open) => !open)}
                        >
                            <MoreHorizontal className="size-5" aria-hidden="true" />
                        </button>
                        {menuOpen && (
                            <div className="absolute top-9 right-0 z-10 min-w-32 rounded-xl border border-[var(--line)] bg-[var(--surface)] p-1.5 shadow-md">
                                <button
                                    className="flex w-full cursor-pointer items-center gap-2 rounded-lg border-0 bg-transparent px-3 py-2 text-left text-xs font-semibold text-red-500 hover:bg-red-500/10 disabled:cursor-not-allowed disabled:opacity-50"
                                    type="button"
                                    disabled={busy}
                                    onClick={() => {
                                        setMenuOpen(false)
                                        setConfirmDeleteOpen(true)
                                    }}
                                >
                                    <Trash2 className="size-4" aria-hidden="true" />{busy ? 'Deleting…' : 'Delete post'}
                                </button>
                            </div>
                        )}
                    </div>
                )}
            </header>

            {showLocation && (
                <Link
                    className="mt-2.5 inline-flex items-center gap-1.5 text-xs font-bold text-[var(--accent)] no-underline hover:underline transition-colors"
                    to="/map"
                    state={{ selectedLocation: { locationID: post.locationID, name: post.locationName, address: post.address, latitude: post.latitude, longitude: post.longitude } }}
                >
                    <MapPin className="size-4 text-[#ea4335]" aria-hidden="true" />
                    <span>{post.locationName}</span>
                </Link>
            )}

            <p className="mt-3 mb-0 whitespace-pre-wrap text-sm leading-relaxed text-[var(--ink)] font-normal">{post.description}</p>

            {post.imageUrls.length > 0 && (
                <div className={`mt-3.5 grid max-h-[25rem] gap-2 overflow-hidden rounded-xl ${imageGridClassName}`}>
                    {post.imageUrls.map((url, index) => (
                        <img
                            className={`${post.imageUrls.length === 1 ? 'max-h-[25rem]' : post.imageUrls.length === 3 && index === 0 ? 'row-span-2 h-full min-h-40' : 'aspect-[4/3]'} block w-full rounded-xl border border-[var(--line)] bg-[var(--soft-surface)] object-cover`}
                            src={url}
                            alt={`Weather observation ${index + 1}`}
                            loading="lazy"
                            key={url}
                        />
                    ))}
                </div>
            )}

            <div className="mt-3.5 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-[var(--line)] bg-[var(--soft-surface)] p-3">
                <div className="flex min-w-0 items-center gap-3">
                    <img
                        className="size-9 shrink-0 object-contain"
                        src={getWeatherArtwork({ weatherCode: post.apiWeather.weather_code, daytime: recordedDuringDay(post.apiWeather.recordedAt) })}
                        alt=""
                        aria-hidden="true"
                    />
                    <div className="min-w-0">
                        <div className="flex items-center gap-2">
                            <strong className="truncate text-xs font-bold text-[var(--ink)]">{post.apiWeather.condition}</strong>
                            <span className="rounded bg-[var(--surface)] px-1.5 py-0.5 text-[0.62rem] font-bold text-[var(--muted)] border border-[var(--line)]">Open-Meteo</span>
                        </div>
                        <span className="mt-0.5 block truncate text-[0.66rem] text-[var(--muted)] font-medium">Recorded at {recordedTime(post.apiWeather.recordedAt)}</span>
                    </div>
                </div>

                <div className="flex items-center gap-2.5 rounded-xl bg-[var(--surface)] px-3 py-2 shadow-sm border border-[var(--line)] max-[430px]:w-full max-[430px]:justify-between">
                    <div className="flex gap-1" aria-label={`${post.weatherAccuracyRating} out of 5 stars`}>
                        {[1, 2, 3, 4, 5].map((rating) => (
                            <Star
                                className={`size-4 ${
                                    rating <= post.weatherAccuracyRating
                                        ? 'fill-[#f59e0b] text-[#f59e0b] drop-shadow-sm'
                                        : 'fill-slate-300 text-slate-300 dark:fill-slate-700 dark:text-slate-700'
                                }`}
                                aria-hidden="true"
                                key={rating}
                            />
                        ))}
                    </div>
                    <span className="text-[0.72rem] font-extrabold text-[var(--ink)] border-l border-[var(--line)] pl-2.5">
                        {ratingLabel} accurate
                    </span>
                </div>
            </div>

            <section
                className="mt-4 rounded-2xl border border-[var(--feedback-panel-border)] bg-[var(--feedback-panel-background)] px-4 py-3.5 shadow-[var(--feedback-panel-shadow)]"
                aria-label="Community usefulness feedback"
            >
                <div className="flex items-center justify-between gap-5 max-[560px]:items-start max-[560px]:gap-3">
                    <div className="min-w-0 flex-1">
                        <strong className="block text-sm font-extrabold text-[var(--ink)]">Was this report useful?</strong>
                        <span className="mt-1 block max-w-sm text-[0.72rem] leading-[1.45] text-[var(--muted)]">Community feedback, separate from the author’s weather accuracy.</span>
                    </div>

                    <div className="flex shrink-0 items-center gap-5 max-[430px]:gap-3" role="group" aria-label="Report usefulness counts">
                        {isOwner ? (
                            <>
                                <span className="inline-flex items-center gap-2 text-xs font-extrabold text-[var(--ink)]" aria-label={`${helpfulCount} helpful votes`}>
                                    <ThumbsUp className="size-5 fill-current stroke-[1.8] text-[var(--feedback-helpful-icon)]" aria-hidden="true" />
                                    {helpfulCount}
                                </span>
                                <span className="inline-flex items-center gap-2 border-l border-[var(--line)] pl-5 text-xs font-extrabold text-[var(--ink)] max-[430px]:pl-3" aria-label={`${notHelpfulCount} not helpful votes`}>
                                    <ThumbsDown className="size-5 stroke-[2] text-[var(--feedback-neutral-icon)]" aria-hidden="true" />
                                    {notHelpfulCount}
                                </span>
                            </>
                        ) : (
                            <>
                                <button
                                    type="button"
                                    className="group inline-flex cursor-pointer items-center gap-2 rounded-md border-0 bg-transparent p-1 text-xs font-extrabold text-[var(--ink)] transition-transform hover:-translate-y-0.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--feedback-helpful-icon)] disabled:cursor-not-allowed disabled:opacity-55 disabled:hover:translate-y-0"
                                    disabled={feedbackBusy !== null}
                                    aria-pressed={myFeedback === 'HELPFUL'}
                                    aria-label={`Mark this report helpful. ${helpfulCount} helpful votes`}
                                    aria-busy={feedbackBusy === 'HELPFUL'}
                                    onClick={() => void submitFeedback('HELPFUL')}
                                >
                                    <ThumbsUp className={`size-5 stroke-[1.8] text-[var(--feedback-helpful-icon)] transition-all group-hover:fill-current ${myFeedback === 'HELPFUL' ? 'scale-110 fill-current' : 'fill-transparent'} ${feedbackBusy === 'HELPFUL' ? 'animate-pulse' : ''}`} aria-hidden="true" />
                                    <span aria-hidden="true">{helpfulCount}</span>
                                </button>
                                <button
                                    type="button"
                                    className="group inline-flex cursor-pointer items-center gap-2 rounded-r-md border-0 border-l border-[var(--line)] bg-transparent py-1 pr-1 pl-5 text-xs font-extrabold text-[var(--ink)] transition-transform hover:-translate-y-0.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--feedback-neutral-icon)] disabled:cursor-not-allowed disabled:opacity-55 disabled:hover:translate-y-0 max-[430px]:pl-3"
                                    disabled={feedbackBusy !== null}
                                    aria-pressed={myFeedback === 'NOT_HELPFUL'}
                                    aria-label={`Mark this report not helpful. ${notHelpfulCount} not helpful votes`}
                                    aria-busy={feedbackBusy === 'NOT_HELPFUL'}
                                    onClick={() => void submitFeedback('NOT_HELPFUL')}
                                >
                                    <ThumbsDown className={`size-5 stroke-[2] text-[var(--feedback-neutral-icon)] transition-all group-hover:fill-current ${myFeedback === 'NOT_HELPFUL' ? 'scale-110 fill-current' : 'fill-transparent'} ${feedbackBusy === 'NOT_HELPFUL' ? 'animate-pulse' : ''}`} aria-hidden="true" />
                                    <span aria-hidden="true">{notHelpfulCount}</span>
                                </button>
                            </>
                        )}
                    </div>
                </div>
            </section>

            {error && <p className="mt-2.5 mb-0 rounded-xl bg-red-50 dark:bg-red-950/50 border border-red-200 dark:border-red-900 px-3.5 py-2 text-xs font-semibold text-red-600 dark:text-red-300" role="alert">{error}</p>}

            {confirmDeleteOpen && createPortal(
                <div
                    className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4 animate-in fade-in duration-200"
                    onClick={() => setConfirmDeleteOpen(false)}
                >
                    <div
                        className="w-full max-w-sm rounded-3xl bg-[var(--surface)] p-6 shadow-xl border border-[var(--line)] transition-all scale-100"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div className="mx-auto flex size-12 items-center justify-center rounded-full bg-red-50 dark:bg-red-950/50 text-red-600 dark:text-red-400 mb-4">
                            <Trash2 className="size-6" aria-hidden="true" />
                        </div>
                        <h3 className="text-center text-lg font-bold text-[var(--ink)]">Delete post?</h3>
                        <p className="mt-2 text-center text-xs leading-relaxed text-[var(--muted)] font-medium">
                            Are you sure you want to delete this community weather observation? This action cannot be undone.
                        </p>
                        <div className="mt-6 flex items-center gap-3">
                            <button
                                type="button"
                                className="flex-1 cursor-pointer rounded-2xl border border-[var(--line)] bg-[var(--soft-surface)] px-4 py-2.5 text-xs font-bold text-[var(--ink)] hover:bg-[var(--surface-hover)] transition-colors"
                                onClick={() => setConfirmDeleteOpen(false)}
                            >
                                Cancel
                            </button>
                            <button
                                type="button"
                                className="flex-1 cursor-pointer rounded-2xl bg-red-600 px-4 py-2.5 text-xs font-bold text-white shadow-sm hover:bg-red-700 transition-colors disabled:opacity-50"
                                disabled={busy}
                                onClick={executeRemove}
                            >
                                {busy ? 'Deleting…' : 'Delete'}
                            </button>
                        </div>
                    </div>
                </div>,
                document.body
            )}
        </article>
    )
}
