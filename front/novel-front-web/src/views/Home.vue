<template>
  <Header />
  <div class="main box_center cf">
    <div class="channelWrap channelBanner cf">
      <div class="leftBox home-hero-left">
        <div class="sliderContent home-hero-carousel">
          <dl class="scBigImg" id="carouseBig">
            <dd
              v-for="(item, index) in sliderContent"
              :key="index"
              :class="{ on: index == 0 }"
            >
              <a href="javascript:void(0)" @click="bookDetail(item.bookId)">
                <img
                  :src="`${imgBaseUrl}` + `${item.picUrl}`"
                  :alt="item.bookName"
                  onerror="this.src='default.gif';this.onerror=null"
                />
                <span class="home-carousel-name">{{ item.bookName }}</span>
              </a>
            </dd>
          </dl>
          <div class="scSmallImg" id="carouseSmall">
            <ul>
              <li
                v-for="(item, index) in sliderContent"
                :key="index"
                :class="{ on: index == 0 }"
              >
                <img
                  :src="`${imgBaseUrl}` + `${item.picUrl}`"
                  :alt="item.bookName"
                  onerror="this.src='default.gif';this.onerror=null"
                />
              </li>
            </ul>
          </div>
        </div>
        <div class="hot_articles home-spotlight" id="topBooks2">
          <div class="home-spotlight-grid" v-if="topBooks1.length">
            <a
              v-for="(item, index) in topBooks1.slice(0, 8)"
              :key="item.bookId || index"
              class="home-spotlight-book"
              href="javascript:void(0)"
              @click="bookDetail(item.bookId)"
            >
              <span class="home-spotlight-cover">
                <img
                  :src="`${imgBaseUrl}` + `${item.picUrl}`"
                  :alt="item.bookName"
                  onerror="this.src='default.gif';this.onerror=null"
                />
              </span>
              <span class="home-spotlight-info">
                <strong>{{ item.bookName }}</strong>
                <em>{{ item.authorName || '作者待更新' }}</em>
              </span>
            </a>
          </div>
          <div class="home-spotlight-empty" v-else>暂无推荐小说</div>

          <LatestNews class="home-spotlight-news" />
        </div>
      </div>
      <div class="rightBox">
        <div class="title cf" id="weekcommend">
          <h3>本周强推</h3>
        </div>
        <div class="rightList">
          <ul id="currentWeek">
            <li
              v-for="(item, index) in weekcommend"
              :key="index"
              :class="['num' + (Number(`${index}`) + 1), { on: index == 0 }]"
            >
              <div class="book_name">
                <i>{{ index + 1 }}</i
                ><a class="name" href="javascript:void(0)" @click="bookDetail(item.bookId)">{{
                  item.bookName
                }}</a>
              </div>
              <div class="book_intro">
                <div class="cover">
                  <a href="javascript:void(0)" @click="bookDetail(item.bookId)"
                    ><img
                      :src="`${imgBaseUrl}` + `${item.picUrl}`"
                      :alt="item.bookName"
                      onerror="this.src='default.gif';this.onerror=null"
                  /></a>
                </div>
                <a
                  class="txt"
                  href="javascript:void(0)" @click="bookDetail(item.bookId)"
                  v-html="item.bookDesc"
                ></a>
              </div>
            </li>
          </ul>
        </div>
      </div>
    </div>

    <div class="channelWrap channelPic cf">
      <div class="leftBox">
        <div class="title">
          <h2 class="on">热门推荐</h2>
        </div>
        <div class="picRecommend cf" id="hotRecBooks">
          <div
            class="itemsList"
            v-for="(item, index) in hotRecommend"
            :key="index"
          >
            <a class="items_img" href="javascript:void(0)" @click="bookDetail(item.bookId)">
              <img
                :src="`${imgBaseUrl}` + `${item.picUrl}`"
                onerror="this.src='default.gif';this.onerror=null"
                :alt="item.bookName"
              />
            </a>
            <div class="items_txt">
              <h4>
                <a href="javascript:void(0)" @click="bookDetail(item.bookId)">{{ item.bookName }}</a>
              </h4>
              <p class="author">
                <a href="javascript:void(0)">作者：{{ item.authorName }}</a>
              </p>
              <p class="intro">
                <a
                  href="javascript:void(0)" @click="bookDetail(item.bookId)"
                  v-html="item.bookDesc"
                ></a>
              </p>
            </div>
          </div>
        </div>
      </div>
      <BookVisitRank />
    </div>
    <div class="channelWrap channelPic cf">
      <div class="leftBox">
        <div class="title">
          <h2>精品推荐</h2>
        </div>

        <div class="picRecommend cf" id="classicBooks">
          <div
            class="itemsList"
            v-for="(item, index) in goodRecommend"
            :key="index"
          >
            <a class="items_img" href="javascript:void(0)" @click="bookDetail(item.bookId)">
              <img
                :src="`${imgBaseUrl}` + `${item.picUrl}`"
                onerror="this.src='default.gif';this.onerror=null"
              />
            </a>
            <div class="items_txt">
              <h4>
                <a href="javascript:void(0)" @click="bookDetail(item.bookId)">{{ item.bookName }}</a>
              </h4>
              <p class="author">
                <a href="javascript:void(0)">作者：{{ item.authorName }}</a>
              </p>
              <p class="intro">
                <a
                  href="javascript:void(0)" @click="bookDetail(item.bookId)"
                  v-html="item.bookDesc"
                ></a>
              </p>
            </div>
          </div>
        </div>
      </div>
      <BookNewestRank />
    </div>

    <BookUpdateRank />
  </div>
  <FriendLink />

  <Footer />
</template>

<script>
import { reactive, toRefs, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import { listHomeBooks } from "@/api/home";
import { ElMessage, ElLoading } from "element-plus";
import Header from "@/components/common/Header";
import Footer from "@/components/common/Footer";
import LatestNews from "@/components/home/LatestNews";
import FriendLink from "@/components/home/FriendLink";
import BookVisitRank from "@/components/home/BookVisitRank";
import BookNewestRank from "@/components/home/BookNewestRank";
import BookUpdateRank from "@/components/home/BookUpdateRank";
export default {
  name: "home",
  components: {
    Header,
    LatestNews,
    FriendLink,
    BookVisitRank,
    BookNewestRank,
    BookUpdateRank,
    Footer,
  },
  setup() {
    const route = useRoute();
    const router = useRouter();
    const state = reactive({
      // 轮播图
      sliderContent: [],
      // 顶部栏
      topBooks1: [],
      //本周强推
      weekcommend: [],
      // 热门推荐
      hotRecommend: [],
      // 精品推荐
      goodRecommend: [],
      imgBaseUrl: process.env.VUE_APP_BASE_IMG_URL,
    });

    onMounted(async () => {
      const loadingInstance = ElLoading.service({
        target: "#topBooks2",
        text: "加载中。。。",
      });
      const { data } = await listHomeBooks();
      loadingInstance.close();

      await data.forEach((book) => {
        if (book.type == 0) {
          // 轮播图
          state.sliderContent[state.sliderContent.length] = book;
        }
        if (book.type == 1) {
          // 顶部栏
          state.topBooks1[state.topBooks1.length] = book;
        }
        if (book.type == 2) {
          //本周强推
          state.weekcommend[state.weekcommend.length] = book;
        }
        if (book.type == 3) {
          //热门推荐
          state.hotRecommend[state.hotRecommend.length] = book;
        }
        if (book.type == 4) {
          //精品推荐
          state.goodRecommend[state.goodRecommend.length] = book;
        }
      });

      var $div = $(".scBigImg dl"); //放置大图容器
      var $nav = $(".scSmallImg li"); //放置缩略图容器
      var num = -1;
      var open;

      function changeKv() {
        if (num >= $nav.length - 1) {
          num = 0;
        } else {
          num++;
        }
        $nav.eq(num).trigger("mouseenter");
        open = setTimeout(changeKv, 3000);
      }

      changeKv();
      $nav.each(function (index) {
        $(this)
          .off("mouseenter")
          .on("mouseenter", function () {
            clearTimeout(open);

            $(this).addClass("on").siblings().removeClass("on");
            $(".scBigImg dd")
              .eq(index)
              .addClass("on")
              .siblings()
              .removeClass("on");
            $(".scSmallImg")
              .off("mouseleave")
              .on("mouseleave", function () {
                num = index;
                setTimeout(function () {
                  changeKv();
                }, 3000);
              });
          });
      });
      $div.each(function () {
        $(this)
          .off("mouseenter")
          .on("mouseenter", function () {
            clearTimeout(open);
          });
      });
    });

    const bookDetail = (bookId) => {
      router.push({ path: `/book/${bookId}` });
    };

    return {
      ...toRefs(state),
      bookDetail,
    };
  }
};
</script>

<style scoped>
.home-hero-left {
  display: flex;
  gap: 18px;
  align-items: flex-start;
}

.home-hero-left .home-hero-carousel {
  flex: 0 0 306px;
  float: none;
  width: 306px;
}

.home-hero-carousel .scBigImg a {
  position: relative;
  display: block;
  width: 240px;
  height: 300px;
  overflow: hidden;
  border-radius: 4px;
  background: #f6f6f6;
}

.home-hero-carousel .scBigImg img {
  display: block;
  width: 240px;
  height: 300px;
  object-fit: cover;
}

.home-carousel-name {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  box-sizing: border-box;
  height: 38px;
  padding: 0 12px;
  overflow: hidden;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  line-height: 38px;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0), rgba(0, 0, 0, .68));
}

.home-hero-carousel .scSmallImg {
  width: 54px;
}

.home-hero-carousel .scSmallImg li {
  width: 50px;
  height: 58px;
  margin-bottom: 7px;
  overflow: hidden;
  border-radius: 3px;
}

.home-hero-carousel .scSmallImg img {
  display: block;
  width: 50px;
  height: 58px;
  object-fit: cover;
}

.home-hero-left .home-spotlight {
  flex: 1;
  float: none;
  width: auto;
  min-width: 0;
  padding: 0;
}

.home-spotlight-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.home-spotlight-book {
  display: flex;
  min-width: 0;
  height: 66px;
  box-sizing: border-box;
  padding: 7px;
  border: 1px solid #eee;
  border-radius: 4px;
  background: #fff;
  transition: border-color .2s, background-color .2s, transform .2s;
}

.home-spotlight-book:hover {
  color: #333;
  border-color: #ffd7ad;
  background: #fffaf4;
  transform: translateY(-1px);
}

.home-spotlight-cover {
  flex: 0 0 40px;
  width: 40px;
  height: 52px;
  overflow: hidden;
  border-radius: 3px;
  background: #f6f6f6;
}

.home-spotlight-cover img {
  display: block;
  width: 40px;
  height: 52px;
  object-fit: cover;
}

.home-spotlight-info {
  display: block;
  min-width: 0;
  padding-left: 9px;
}

.home-spotlight-info strong,
.home-spotlight-info em {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-spotlight-info strong {
  height: 24px;
  color: #333;
  font-size: 14px;
  font-weight: 600;
  line-height: 24px;
}

.home-spotlight-info em {
  height: 20px;
  color: #999;
  font-size: 12px;
  line-height: 20px;
}

.home-spotlight-empty {
  height: 142px;
  color: #999;
  line-height: 142px;
  text-align: center;
  border: 1px dashed #eee;
  border-radius: 4px;
  background: #fafafa;
}

.home-spotlight :deep(.hot_notice) {
  margin-top: 12px;
  padding: 10px 12px 0;
  border-top: 1px dotted #eae6e2;
}

.home-spotlight :deep(.hot_notice dd) {
  display: flex;
  gap: 8px;
  min-width: 0;
  height: 25px;
  line-height: 25px;
}

.home-spotlight :deep(.hot_notice span) {
  flex: 0 0 auto;
  color: #f70;
}

.home-spotlight :deep(.hot_notice a) {
  display: block;
  min-width: 0;
  overflow: hidden;
  color: #666;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
