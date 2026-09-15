import { MessageSquare, Plus } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { LocationSearchResult } from '../../../api/locations'
import { useAuthStore } from '../../../stores/authStore'
import { getLocationPosts } from '../../community-post/service/postService'
import type { CommunityPost } from '../../community-post/types'
import { CommunityPostCard } from '../../community-post/ui/CommunityPostCard'
import { CreatePostModal } from './CreatePostModal'

export function LocationPosts({ location }: { location: LocationSearchResult }) {
    return <LocationPostsContent location={location} key={location.locationID} />
}

function LocationPostsContent({ location }: { location: LocationSearchResult }) {
    const currentUser = useAuthStore((state) => state.user)
    const [posts, setPosts] = useState<CommunityPost[]>([])
    const [cursor, setCursor] = useState<string | null>(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const [createPostOpen, setCreatePostOpen] = useState(false)

    useEffect(() => {
        const controller = new AbortController()
        void getLocationPosts(location.locationID, undefined, controller.signal)
            .then((page) => { setPosts(page.items); setCursor(page.nextCursor) })
            .catch((cause) => { if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : 'Posts could not be loaded.') })
            .finally(() => { if (!controller.signal.aborted) setLoading(false) })
        return () => controller.abort()
    }, [location.locationID])

    async function loadMore() {
        if (!cursor) return
        setLoading(true); setError('')
        try {
            const page = await getLocationPosts(location.locationID, cursor)
            setPosts((current) => [...current, ...page.items]); setCursor(page.nextCursor)
        } catch (cause) { setError(cause instanceof Error ? cause.message : 'More posts could not be loaded.') }
        finally { setLoading(false) }
    }

    return (
        <section className="mt-5 border-t border-white/20 pt-5" aria-labelledby="location-community-title">
            <header className="flex items-center justify-between gap-4 max-[430px]:flex-col max-[430px]:items-stretch">
                <div>
                    <h3 className="m-0 text-lg font-bold text-[var(--ink)]" id="location-community-title">Community Posts</h3>
                    <p className="mt-1 mb-0 text-xs text-[var(--muted)]">See what others are experiencing at this location.</p>
                </div>
                <button className="inline-flex min-h-10 shrink-0 cursor-pointer items-center justify-center gap-2 rounded-full bg-[var(--action-background)] hover:bg-[var(--action-hover-background)] px-5 text-xs font-bold text-[var(--action-ink)] shadow-md transition-all max-[430px]:w-full" type="button" onClick={() => setCreatePostOpen(true)}>
                    <Plus className="size-4" aria-hidden="true" />
                    <span>Create Post</span>
                </button>
            </header>

            {error && <p className="mt-3.5 mb-0 rounded-xl bg-red-50 border border-red-200 px-3.5 py-2 text-xs font-semibold text-red-600" role="alert">{error}</p>}
            {loading && posts.length === 0 && <p className="mt-5 text-center text-xs font-medium text-[var(--muted)]" role="status">Loading community posts…</p>}

            {!loading && posts.length === 0 && (
                <div className="mt-4 grid place-items-center rounded-2xl border border-white/30 bg-[var(--glass-inset-background)] p-8 text-center shadow-sm">
                    <span className="grid size-12 place-items-center rounded-full bg-blue-500/10 text-[var(--accent)]"><MessageSquare className="size-6" aria-hidden="true" /></span>
                    <h4 className="mt-3.5 mb-0 text-base font-bold text-[var(--ink)]">No posts here yet</h4>
                    <p className="mt-1.5 mb-0 max-w-md text-xs leading-relaxed text-[var(--muted)] font-medium">Be the first to share what the weather is like at this location.</p>
                    <button className="mt-4 inline-flex min-h-10 cursor-pointer items-center gap-2 rounded-full bg-[var(--action-background)] hover:bg-[var(--action-hover-background)] px-5 text-xs font-bold text-[var(--action-ink)] shadow-md transition-all" type="button" onClick={() => setCreatePostOpen(true)}><Plus className="size-4" aria-hidden="true" />Create Post</button>
                </div>
            )}

            <div className="mt-4 grid gap-3.5">{posts.map((post) => <CommunityPostCard post={post} currentUserID={currentUser?.userID} currentUsername={currentUser?.name} onDeleted={(postID) => setPosts((current) => current.filter((item) => item.postID !== postID))} key={post.postID} />)}</div>

            {cursor && <button className="mt-4 w-full rounded-full border border-white/30 bg-[var(--glass-inset-background)] hover:bg-white/20 px-4 py-2.5 text-xs font-bold text-[var(--ink)] shadow-sm transition-colors cursor-pointer" disabled={loading} onClick={() => void loadMore()}>{loading ? 'Loading more posts…' : 'Load more posts'}</button>}

            {createPostOpen && (
                <CreatePostModal
                    location={location}
                    onClose={() => setCreatePostOpen(false)}
                    onCreated={(post) => setPosts((current) => [post, ...current])}
                />
            )}
        </section>
    )
}
