package com.cyl.musiclake.ui.music.playpage

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.ImageView
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import com.cyl.musiclake.R
import com.cyl.musiclake.common.Extras
import com.cyl.musiclake.common.TransitionAnimationUtils
import com.cyl.musiclake.event.MetaChangedEvent
import com.cyl.musiclake.event.PlayModeEvent
import com.cyl.musiclake.event.StatusChangedEvent
import com.cyl.musiclake.ui.UIUtils
import com.cyl.musiclake.ui.base.BaseActivity
import com.cyl.musiclake.ui.music.comment.SongCommentActivity
import com.cyl.musiclake.ui.music.dialog.BottomDialogFragment
import com.cyl.musiclake.ui.music.dialog.MusicLyricDialog
import com.cyl.musiclake.ui.music.dialog.QualitySelectDialog
import com.cyl.musiclake.ui.music.edit.PlaylistManagerUtils
import com.cyl.musiclake.ui.music.local.adapter.MyViewPagerAdapter
import com.cyl.musiclake.ui.music.playpage.fragment.CoverFragment
import com.cyl.musiclake.ui.music.playpage.fragment.LyricFragment
import com.cyl.musiclake.ui.music.playqueue.PlayQueueDialog
import com.cyl.musiclake.ui.widget.DepthPageTransformer
import com.cyl.musiclake.ui.widget.MultiTouchViewPager
import com.cyl.musiclake.utils.FloatLyricViewManager
import com.cyl.musiclake.utils.FormatUtil
import com.cyl.musiclake.utils.LogUtil
import com.cyl.musiclake.utils.Tools
import com.music.lake.musiclib.bean.BaseMusicInfo
import com.music.lake.musiclib.player.MusicPlayerManager
import kotlinx.android.synthetic.main.activity_player.*
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.jetbrains.anko.startActivity
import kotlin.math.max
import kotlin.math.min

class PlayerActivity : BaseActivity<PlayPresenter>(), PlayContract.View {
    private var playingBaseMusicInfoInfo: BaseMusicInfo? = null
    private var coverFragment: CoverFragment? = CoverFragment()
    private var lyricFragment: LyricFragment? = LyricFragment()

    private val fragments = mutableListOf<Fragment>()

    /***
     * 显示当前正在播放
     */
    override fun showNowPlaying(baseMusicInfo: BaseMusicInfo?) {
        if (baseMusicInfo == null) finish()

        playingBaseMusicInfoInfo = baseMusicInfo as BaseMusicInfo?
        //更新标题
        titleIv.text = baseMusicInfo?.title
        subTitleTv.text = baseMusicInfo?.artist
        //更新类型
        playingBaseMusicInfoInfo?.let { coverFragment?.updateMusicType(it) }
        //更新收藏状态
        baseMusicInfo?.isLove?.let {
            collectIv.setImageResource(if (it) R.drawable.item_favorite_love else R.drawable.item_favorite)
        }
        //更新下载状态
//        downloadIv.visibility = if (BuildConfig.HAS_DOWNLOAD && !music?.isDl!!) View.VISIBLE else View.GONE
        LogUtil.d("PlayerActivity", "showNowPlaying 开始旋转动画")
        //开始旋转动画
        coverFragment?.startRotateAnimation(MusicPlayerManager.getInstance().isPlaying())
    }

    override fun getLayoutResID(): Int {
        return R.layout.activity_player
    }

    override fun hasToolbar(): Boolean {
        return false
    }

    override fun initView() {
        detailView.animation = moveToViewLocation()
        updatePlayMode()

        //歌词搜索
        searchLyricIv.setOnClickListener {
            MusicLyricDialog().apply {
                title = playingBaseMusicInfoInfo?.title
                artist = playingBaseMusicInfoInfo?.artist
                duration = MusicPlayerManager.getInstance().getDuration()
                searchListener = {
                }
                textSizeListener = {
                    lyricFragment?.lyricTv?.setTextSize(it)
                }
                textColorListener = {
                    lyricFragment?.lyricTv?.setHighLightTextColor(it)
                }
                lyricListener = {
                    lyricFragment?.lyricTv?.setLyricContent(it)
                }
            }.show(this)

        }
    }

    override fun updatePlayMode() {
        UIUtils.updatePlayMode(playModeIv, isChange = false)
    }

    override fun updateProgress(progress: Long, max: Long, bufferPercent: Int) {
        runOnUiThread {
            if (!isPause) {
                progressSb.max = 100
                progressSb.progress = if (max <= 0) 0 else max(0, min((progress * 100 / max).toInt(), 100))
                progressSb.secondaryProgress = bufferPercent

                progressTv.text = FormatUtil.formatTime(progress)
                durationTv.text = FormatUtil.formatTime(max)
                lyricFragment?.setCurrentTimeMillis(progress)
            }
        }
    }

    override fun initData() {
        setupViewPager(viewPager)
        coverFragment?.initAlbumPic()
        mPresenter?.updateNowPlaying(MusicPlayerManager.getInstance().getNowPlayingMusic(), true)
        //更新播放状态
        updatePlayStatus(MusicPlayerManager.getInstance().isPlaying())
        lyricFragment?.showLyric(FloatLyricViewManager.lyricInfo, true)
        LogUtil.d("CoverFragment", "playingMusic =${playingBaseMusicInfoInfo == null}")
        playingBaseMusicInfoInfo?.let { coverFragment?.updateMusicType(it) }
    }

    override fun listener() {
        super.listener()
        backIv.setOnClickListener {
            closeActivity()
        }
        progressSb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.progress?.let {
                    MusicPlayerManager.getInstance().seekTo(it.toLong())
                    lyricFragment?.setCurrentTimeMillis(it.toLong())
                }
            }

        })
        playPauseIv.setOnClickListener {
            MusicPlayerManager.getInstance().pausePlay()
        }

        /**
         * 歌曲操作
         */
        operateSongIv.setOnClickListener {
            BottomDialogFragment.newInstance(playingBaseMusicInfoInfo)
                    .show(this)
        }
    }

    override fun initInjector() {
        mActivityComponent.inject(this)
    }


    fun nextPlay(view: View?) {
        if (UIUtils.isFastClick()) return
        MusicPlayerManager.getInstance().playNextMusic()
    }

    fun prevPlay(view: View?) {
        if (UIUtils.isFastClick()) return
        MusicPlayerManager.getInstance().playPrevMusic()
    }

    fun changePlayMode(view: View?) {
        UIUtils.updatePlayMode(view as ImageView, isChange = true)
    }

    /**
     * 打开播放队列
     */
    fun openPlayQueue(view: View?) {
        PlayQueueDialog.newInstance().showIt(this)
    }

    /**
     * 歌曲收藏
     */
    fun collectMusic(view: View?) {
        UIUtils.collectMusic(view as ImageView, playingBaseMusicInfoInfo)
    }

    /**
     * 添加到歌單
     */
    fun addToPlaylist(view: View?) {
        PlaylistManagerUtils.addToPlaylist(this, playingBaseMusicInfoInfo)
    }

    /**
     * 添加到歌單
     */
    fun showSongComment(view: View?) {
        startActivity<SongCommentActivity>(Extras.SONG to playingBaseMusicInfoInfo)
    }

    /**
     * 分享歌曲
     * TODO 增加海报，截屏分享
     */
    fun shareMusic(view: View?) {
        Tools.qqShare(this, MusicPlayerManager.getInstance().getNowPlayingMusic())
    }

    /**
     * 歌曲下载
     */
    fun downloadMusic(view: View?) {
        QualitySelectDialog.newInstance(playingBaseMusicInfoInfo).apply {
            isDownload = true
        }.show(this)
    }

    override fun setPlayingBitmap(albumArt: Bitmap?) {
        coverFragment?.setImageBitmap(albumArt)
    }

    override fun setPlayingBg(albumArt: Drawable?, isInit: Boolean?) {
        if (isInit != null && isInit) {
            playingBgIv.setImageDrawable(albumArt)
        } else {
            //加载背景图过度
            TransitionAnimationUtils.startChangeAnimation(playingBgIv, albumArt)
        }
    }

    override fun updatePlayStatus(isPlaying: Boolean) {
        if (isPlaying && !playPauseIv.isPlaying) {
            playPauseIv.play()
            coverFragment?.resumeRotateAnimation()
        } else if (!isPlaying && playPauseIv.isPlaying) {
            coverFragment?.stopRotateAnimation()
            playPauseIv.pause()
        }
    }

    private fun setupViewPager(viewPager: MultiTouchViewPager) {
        fragments.clear()
        coverFragment?.let {
            fragments.add(it)
        }
        lyricFragment?.let {
            fragments.add(it)
        }
        val mAdapter = MyViewPagerAdapter(supportFragmentManager, fragments)

        viewPager.adapter = mAdapter
        viewPager.setPageTransformer(false, DepthPageTransformer())
        viewPager.offscreenPageLimit = 2
        viewPager.currentItem = 0
        viewPager.addOnPageChangeListener(object : androidx.viewpager.widget.ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                LogUtil.d("PlayControlFragment", "--$position")
                if (position == 0) {
                    searchLyricIv.visibility = View.GONE
                    operateSongIv.visibility = View.VISIBLE
                    lyricFragment?.lyricTv?.setIndicatorShow(false)
                    rightTv.isChecked = false
                    leftTv.isChecked = true
                } else {
                    searchLyricIv.visibility = View.VISIBLE
                    operateSongIv.visibility = View.GONE
                    leftTv.isChecked = false
                    rightTv.isChecked = true
                }
            }

            override fun onPageScrollStateChanged(state: Int) {

            }
        })
    }

    /**
     * 底部上移动画效果
     */
    private fun moveToViewLocation(): TranslateAnimation {
        val mHiddenAction = TranslateAnimation(Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_SELF,
                1.0f, Animation.RELATIVE_TO_SELF, 0.0f)
        mHiddenAction.duration = 300
        return mHiddenAction
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayModeChangedEvent(event: PlayModeEvent) {
        updatePlayMode()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMetaChangedEvent(event: MetaChangedEvent) {
        mPresenter?.updateNowPlaying(event.baseMusicInfoInfo)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun updatePlayStatus(event: StatusChangedEvent) {
        playPauseIv.setLoading(!event.isPrepared)
        updatePlayStatus(event.isPlaying)
        progressSb?.secondaryProgress = event.percent.toInt()
    }

    override fun onBackPressed() {
        closeActivity()
    }

    /**
     * 关闭当前界面
     */
    private fun closeActivity() {
        super.onBackPressed()
//        finish()
//        overridePendingTransition(0, 0)
//        ActivityCompat.finishAfterTransition(this)
    }


}
