import { ImagePlus, MapPin, Star, X } from 'lucide-react'
import { useEffect, useMemo, useState, type ChangeEvent, type FormEvent } from 'react'
import { createPortal } from 'react-dom'
import type { LocationSearchResult } from '../../../api/locations'
import { createPost } from '../../community-post/service/postService'
import {
    weatherAccuracyRatingLabel,
    type CommunityPost,
    type WeatherAccuracyRating,
} from '../../community-post/types'

type CreatePostModalProps = {
    location: LocationSearchResult
    onClose: () => void
    onCreated: (post: CommunityPost) => void
}

export function CreatePostModal({ location, onClose, onCreated }: CreatePostModalProps) {
    const [description, setDescription] = useState('')
    const [weatherAccuracyRating, setWeatherAccuracyRating] =
        useState<WeatherAccuracyRating | null>(null)
    const [hoverRating, setHoverRating] = useState<WeatherAccuracyRating | null>(null)
    const [images, setImages] = useState<File[]>([])
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState('')
    const previews = useMemo(() => images.map(URL.createObjectURL), [images])

    useEffect(() => () => previews.forEach(URL.revokeObjectURL), [previews])

    useEffect(() => {
        const previousOverflow = document.body.style.overflow
        document.body.style.overflow = 'hidden'
        const closeOnEscape = (event: KeyboardEvent) => {
            if (event.key === 'Escape' && !submitting) onClose()
        }
        window.addEventListener('keydown', closeOnEscape)
        return () => {
            document.body.style.overflow = previousOverflow
            window.removeEventListener('keydown', closeOnEscape)
        }
    }, [onClose, submitting])

    function selectImages(event: ChangeEvent<HTMLInputElement>) {
        const selected = Array.from(event.target.files ?? [])
        event.target.value = ''
        if (images.length + selected.length > 3) {
            setError('A post can contain a maximum of 3 images.')
            return
        }
        if (selected.some((image) =>
            !['image/jpeg', 'image/png', 'image/webp'].includes(image.type)
            || image.size > 5 * 1024 * 1024)) {
            setError('Images must be JPEG, PNG, or WebP and no larger than 5 MB.')
            return
        }
        setImages((current) => [...current, ...selected])
        setError('')
    }

    async function submit(event: FormEvent) {
        event.preventDefault()
        if (!description.trim()) {
            setError('Description is required.')
            return
        }
        if (weatherAccuracyRating === null) {
            setError('Choose how accurately the Open-Meteo condition matches your observation.')
            return
        }

        setSubmitting(true)
        setError('')
        try {
            const post = await createPost(
                location,
                description.trim(),
                weatherAccuracyRating,
                images,
            )
            onCreated(post)
            onClose()
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : 'Post creation failed.')
        } finally {
            setSubmitting(false)
        }
    }

    return createPortal(
        <div
            className="fixed inset-0 z-[2000] grid place-items-center overflow-y-auto bg-slate-950/60 p-5 backdrop-blur-[2px] animate-backdrop max-[520px]:p-3"
            onMouseDown={(event) => {
                if (event.target === event.currentTarget && !submitting) onClose()
            }}
        >
            <section
                className="relative my-auto w-[min(100%,620px)] rounded-3xl border border-[var(--line)] bg-[var(--surface)] p-6 text-[var(--ink)] shadow-xl animate-scale-in max-[520px]:rounded-2xl max-[520px]:p-4"
                role="dialog"
                aria-modal="true"
                aria-labelledby="create-post-title"
                aria-describedby="create-post-subtitle"
            >
                <button
                    className="absolute top-4 right-4 grid size-9 cursor-pointer place-items-center rounded-full border border-[var(--line)] bg-[var(--soft-surface)] text-[var(--muted)] hover:bg-[var(--surface-hover)] hover:text-[var(--ink)] transition-colors disabled:cursor-not-allowed disabled:opacity-50 [&_svg]:size-5"
                    type="button"
                    aria-label="Close create post"
                    disabled={submitting}
                    onClick={onClose}
                >
                    <X aria-hidden="true" />
                </button>

                <header className="pr-11">
                    <h2 className="m-0 text-xl font-bold text-[var(--ink)]" id="create-post-title">Create a Post</h2>
                    <p className="mt-1 mb-0 text-sm text-[var(--muted)]" id="create-post-subtitle">Share the current weather and your experience at this location.</p>
                </header>

                <div className="mt-4 flex items-center gap-3 rounded-2xl border border-[var(--line)] bg-[var(--soft-surface)] p-3.5">
                    <span className="grid size-10 shrink-0 place-items-center rounded-full bg-[#ea4335] text-white shadow-sm"><MapPin className="size-5" aria-hidden="true" /></span>
                    <div className="min-w-0">
                        <strong className="block truncate text-sm font-bold text-[var(--ink)]">{location.name}</strong>
                        <span className="mt-0.5 block text-xs leading-tight text-[var(--muted)] font-medium">{location.address}</span>
                    </div>
                </div>

                <form className="mt-4" onSubmit={(event) => void submit(event)}>
                    <label className="block text-xs font-bold uppercase tracking-wider text-[var(--muted-strong)]" htmlFor="community-post-description">Description <span className="text-red-500">*</span></label>
                    <div className="mt-1.5 rounded-xl border border-[var(--line)] bg-[var(--soft-surface)] p-1 focus-within:border-[var(--accent)] focus-within:bg-[var(--surface)] transition-all">
                        <textarea
                            className="min-h-28 w-full resize-y rounded-lg border-0 bg-transparent p-2.5 text-sm text-[var(--ink)] outline-none placeholder:text-[var(--muted)]"
                            id="community-post-description"
                            maxLength={1000}
                            placeholder="How's the weather? Share your thoughts..."
                            value={description}
                            disabled={submitting}
                            autoFocus
                            onChange={(event) => setDescription(event.target.value)}
                        />
                        <span className="block px-2 pb-1 text-right text-[0.66rem] font-semibold text-[var(--muted)]">{description.length}/1000</span>
                    </div>

                    <div className="mt-4 rounded-2xl border border-[var(--line)] bg-[var(--soft-surface)] p-4 text-center">
                        <label className="block text-xs font-bold uppercase tracking-wider text-[var(--muted-strong)]">
                            Weather accuracy rating <span className="text-red-500">*</span>
                        </label>
                        <p className="mt-1 mb-3 text-xs font-medium text-[var(--muted)]">How accurately does the forecasted weather match your observation?</p>

                        <div className="flex items-center justify-center gap-2">
                            {[1, 2, 3, 4, 5].map((rating) => {
                                const starRating = rating as WeatherAccuracyRating
                                const isActive = (hoverRating ?? weatherAccuracyRating) !== null && rating <= (hoverRating ?? weatherAccuracyRating!)
                                return (
                                    <button
                                        type="button"
                                        key={rating}
                                        className="group cursor-pointer p-1.5 rounded-xl transition-all duration-200 hover:scale-110 active:scale-95 focus:outline-none"
                                        onMouseEnter={() => setHoverRating(starRating)}
                                        onMouseLeave={() => setHoverRating(null)}
                                        onClick={() => setWeatherAccuracyRating(starRating)}
                                    >
                                        <Star
                                            className={`size-7 transition-all duration-200 ${
                                                isActive
                                                    ? 'fill-[#f59e0b] text-[#f59e0b] drop-shadow-[0_2px_8px_rgba(245,158,11,0.45)] scale-105'
                                                    : 'fill-slate-300 text-slate-300 dark:fill-slate-700 dark:text-slate-700 group-hover:text-amber-400 group-hover:fill-amber-50'
                                            }`}
                                        />
                                    </button>
                                )
                            })}
                        </div>

                        <div className="mt-3 min-h-[24px]">
                            {weatherAccuracyRating !== null ? (
                                <span className="inline-flex items-center gap-2 text-xs font-bold text-amber-900 dark:text-amber-300 animate-fade-in">
                                    <span>{weatherAccuracyRating}/5 Stars</span>
                                    <span>•</span>
                                    <span>{weatherAccuracyRatingLabel(weatherAccuracyRating)} accurate</span>
                                </span>
                            ) : (
                                <span className="text-xs font-medium italic text-[var(--muted)]">Click stars to rate (1 = Poor, 5 = Exact)</span>
                            )}
                        </div>
                    </div>

                    <div className="mt-4">
                        <div className="flex items-end justify-between gap-3">
                            <div>
                                <p className="m-0 text-xs font-bold uppercase tracking-wider text-[var(--muted-strong)]">Add photos <span className="normal-case font-normal text-[var(--muted)]">(optional)</span></p>
                                <p className="mt-0.5 mb-0 text-xs text-[var(--muted)]">You can upload up to 3 photos.</p>
                            </div>
                            <span className="text-xs font-bold text-[var(--muted)]">{images.length}/3</span>
                        </div>
                        <label className="mt-2 grid min-h-[68px] cursor-pointer place-items-center rounded-2xl border-2 border-dashed border-[var(--line)] bg-[var(--soft-surface)] px-4 py-3 text-center text-xs font-semibold text-[var(--ink)] hover:bg-[var(--surface-hover)] transition-colors">
                            <span className="inline-flex items-center gap-2 text-[var(--accent)] font-bold"><ImagePlus className="size-5" aria-hidden="true" />Click to choose images</span>
                            <input className="sr-only" type="file" accept="image/jpeg,image/png,image/webp" multiple onChange={selectImages} disabled={submitting || images.length >= 3} />
                        </label>
                        {previews.length > 0 && (
                            <div className="mt-2.5 grid grid-cols-3 gap-2">
                                {previews.map((url, index) => (
                                    <div className="relative overflow-hidden rounded-xl border border-[var(--line)] bg-[var(--soft-surface)]" key={url}>
                                        <img className="aspect-[4/3] w-full object-cover" src={url} alt={`Selected upload ${index + 1}`} />
                                        <button className="absolute top-1.5 right-1.5 grid size-7 cursor-pointer place-items-center rounded-full bg-slate-900/80 text-white hover:bg-slate-900 transition-colors" type="button" aria-label={`Remove image ${index + 1}`} onClick={() => setImages((current) => current.filter((_, itemIndex) => itemIndex !== index))}><X className="size-4" /></button>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>

                    {error && <p className="mt-3 mb-0 rounded-xl bg-red-50 dark:bg-red-950/50 border border-red-200 dark:border-red-900 px-3.5 py-2 text-xs font-semibold text-red-600 dark:text-red-300" role="alert">{error}</p>}

                    <div className="mt-6 flex items-center justify-end gap-3">
                        <button className="min-h-11 cursor-pointer rounded-full border border-[var(--line)] bg-[var(--soft-surface)] px-6 text-xs font-bold text-[var(--ink)] hover:bg-[var(--surface-hover)] transition-colors disabled:cursor-not-allowed disabled:opacity-50" type="button" disabled={submitting} onClick={onClose}>Cancel</button>
                        <button className="min-h-11 cursor-pointer rounded-full border-0 bg-[var(--action-background)] hover:bg-[var(--action-hover-background)] px-7 text-xs font-bold text-[var(--action-ink)] shadow-sm transition-all disabled:cursor-not-allowed disabled:opacity-45" type="submit" disabled={submitting || !description.trim() || weatherAccuracyRating === null}>{submitting ? 'Posting…' : 'Post'}</button>
                    </div>
                </form>
            </section>
        </div>,
        document.body,
    )
}
