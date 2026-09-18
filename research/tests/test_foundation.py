import unittest
from datetime import datetime, timedelta, timezone
import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from smishguard.preprocessing import normalize, feature_text
from smishguard.data import template_groups, split_rows
from smishguard.evaluate import metrics
from smishguard.parity import predict
from smishguard.train import transform
from smishguard.train import margin
from sklearn.linear_model import LogisticRegression
from sklearn.naive_bayes import MultinomialNB
from sklearn.svm import LinearSVC

class FoundationTests(unittest.TestCase):
    def test_uniform_preprocessing(self):
        self.assertEqual(normalize("Bayad 123! https://example.com/a?otp=99"),"bayad zznumberzz zzurlzz")
        self.assertEqual(normalize("İ ñ Á HELLO"), "hello")
    def test_feature_separation(self):
        text="Hello https://example.com/id 123!"
        self.assertNotIn("zzurlzz",feature_text(text,"text-only"))
        for mode in ("placeholders","shape","punctuation"):
            self.assertNotIn("hello",feature_text(text,mode))
    def test_grouping_is_label_blind(self):
        rows=[dict(text_redacted="Bayad na 123",label="0"),dict(text_redacted="Bayad na 999",label="1"),dict(text_redacted="Different greeting",label="0")]
        groups=template_groups(rows)
        self.assertEqual(groups[0],groups[1]); self.assertNotEqual(groups[0],groups[2])
    def test_splits_disjoint_and_repeatable(self):
        rows=[dict(id=str(i),label=str(i%2),received_at=(datetime(2025,1,1,tzinfo=timezone.utc)+timedelta(hours=i)).isoformat()) for i in range(200)]
        groups=[i//2 for i in range(200)]
        for strategy in ("group","random","time"):
            splits=split_rows(rows,groups,strategy,42)
            self.assertEqual(splits,split_rows(rows,groups,strategy,42))
            all_ids=[i for part in splits.values() for i in part]
            self.assertEqual(len(all_ids),len(set(all_ids)))
            self.assertEqual(set(all_ids),set(range(200)))
            if strategy=="group":
                partitions=list(splits.values())
                for i,a in enumerate(partitions):
                    for b in partitions[i+1:]: self.assertFalse({groups[j] for j in a}&{groups[j] for j in b})
        rows[0]["received_at"]=""
        with self.assertRaises(ValueError): split_rows(rows,groups,"time",42)
    def test_train_only_vocabulary(self):
        v=TfidfVectorizer().fit(["training tokens"])
        v.transform(["untouchedtesttoken"])
        self.assertNotIn("untouchedtesttoken",v.vocabulary_)
    def test_metric_math(self):
        m=metrics([0,0,1,1],[.1,.8,.2,.9],.5)
        self.assertEqual(m["false_positive_rate"],.5)
        self.assertEqual(m["false_negative_rate"],.5)
        self.assertAlmostEqual(m["f1"],.5)
    def test_url_facts_are_required_and_separate(self):
        vectorizer=TfidfVectorizer()
        with self.assertRaises(ValueError):
            transform(vectorizer,["bayad dito"],"text-url",fit=True)
        matrix=transform(vectorizer,["bayad dito"],"text-url",fit=True,url_rows=[dict(url_count="1",url_max_host_length="20",url_digit_fraction="0.2",url_max_depth="2")])
        self.assertEqual(matrix.shape[1],len(vectorizer.vocabulary_)+4)
        self.assertTrue(np.all(matrix.toarray()[:,-4:]>=0))
    def test_export_matches_sklearn_tfidf(self):
        v=TfidfVectorizer(lowercase=False,tokenizer=str.split,token_pattern=None).fit(["hello world","hello bayad","account bayad"])
        weights=np.array([.2,-.3,.4,.5])
        model=dict(vocabulary=v.get_feature_names_out().tolist(),idf=v.idf_.tolist(),weights=weights.tolist(),bias=-.1,threshold=0)
        for text in ["hello hello bayad","unseen","ACCOUNT hello"]:
            expected=float((v.transform([normalize(text)])@weights)[0]-.1)
            self.assertAlmostEqual(predict(model,text)[0],expected,places=12)
    def test_all_linear_exports_and_calibration(self):
        # Synthetic SOFTWARE fixtures only. These are not research measurements.
        texts=["hello account","bayad hello","account verify","verify bayad"]
        y=np.array([0,0,1,1])
        v=TfidfVectorizer(lowercase=False).fit(texts)
        x=v.transform(texts)
        for classifier in (LogisticRegression(),MultinomialNB(),LinearSVC(dual="auto")):
            classifier.fit(x,y)
            calibration=LogisticRegression().fit(margin(classifier,x).reshape(-1,1),y)
            if isinstance(classifier,MultinomialNB):
                weights=classifier.feature_log_prob_[1]-classifier.feature_log_prob_[0]
                bias=classifier.class_log_prior_[1]-classifier.class_log_prior_[0]
            else:
                weights=classifier.coef_[0]; bias=classifier.intercept_[0]
            exported=dict(vocabulary=v.get_feature_names_out().tolist(),idf=v.idf_.tolist(),weights=weights.tolist(),bias=float(bias),threshold=.5,
                calibration=dict(a=float(calibration.coef_[0,0]),b=float(calibration.intercept_[0])))
            expected=calibration.predict_proba(margin(classifier,x).reshape(-1,1))[:,1]
            for text,score in zip(texts,expected): self.assertAlmostEqual(predict(exported,text)[0],score,places=12)

if __name__ == "__main__": unittest.main()
