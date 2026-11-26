from docx import Document
import json,re
from pathlib import Path
path=Path(__file__).parent
source=Path('/mnt/data/english_speaking_day22-29_originals_zh_ipa.docx')
doc=Document(source)
sections=[]
current=None
mode=None
for paragraph in doc.paragraphs:
    t=paragraph.text.strip()
    match=re.match(r'^Day (\d+)\s+\|\s+(.+)$',t)
    if match:
        current={'day':int(match.group(1)), 'title':match.group(2),'date':f'2026-11-{int(match.group(1))-4:02}', 'segments': [],'words':[]}
        sections.append(current); mode=None; continue
    if not current: continue
    if t=='英文原文 / English Original': mode='en';continue
    if t=='中文参考译文': mode='zh';continue
    if t.startswith('重点难词'):mode=None;continue
    if not t:continue
    if mode=='en': current['segments'].append({'en':t,'zh':''})
    if mode=='zh':
        target=next((seg for seg in current['segments'] if not seg['zh']),None)
        if target:target['zh']=t
for i,tab in enumerate(doc.tables):
    sections[i]['words']=[{'word':r.cells[0].text.strip(),'ipa':r.cells[1].text.strip(),'meaning':r.cells[2].text.strip()} for r in tab.rows[1:]]
for l in sections:
    l['topic']={22:'职场沟通',23:'旅行场景',24:'软件开发',25:'职场沟通',26:'日常生活',27:'旅行场景',28:'职场沟通',29:'软件开发'}[l['day']]
    l['level']='B1+' if l['day']<29 else 'B2'
    l['expressions']=[]
    l['exercises']=[]
# 5 expressions for every imported article; contextual meaning and example
exp={
22:[('turn out to be','结果发现是','The change turned out to be more challenging than expected.'),('raise an issue','提出问题','I wanted to raise an issue about the schedule.'),('Could we consider...?','能否考虑……？','Could we consider rotating meeting times?'),('overlapping working hours','工作时间的重叠时段','We should meet during overlapping working hours.'),('keep everyone in the loop','让相关人员了解进展','Let’s keep everyone in the loop.')],
23:[('shortly before','就在……之前','Shortly before boarding, the airline announced a delay.'),('estimated departure time','预计起飞时间','Do you have an estimated departure time?'),('I started to wonder whether...','我开始琢磨是否……','I started to wonder whether I would arrive on time.'),('alternative flights','其他可选航班','Are there any alternative flights available?'),('keep you informed','及时通报情况','I’ll keep you informed if anything changes.')],
24:[('look into','调查、排查','We are looking into the performance issue.'),('root cause','根本原因','We have not identified the root cause yet.'),('reduce the immediate impact','减轻眼前影响','We restarted an instance to reduce the immediate impact.'),('at this stage','现阶段','It is difficult to give an exact timeline at this stage.'),('more accurate estimate','更准确的预估','I will provide a more accurate estimate soon.')],
25:[('currently working on','目前正在做……','I’m currently working on a performance issue.'),('longer than expected','比预期更长','The request is taking longer than expected.'),('narrow it down to','缩小范围到……','I have narrowed it down to two possible causes.'),('so far','目前为止','So far, there is no evidence of a memory leak.'),('my next step is to','我的下一步是……','My next step is to run a load test.')],
26:[('look forward to','期待做……','I look forward to spending time with my family.'),('quality time','高质量陪伴时间','I spent some quality time with my family.'),('be tempted to','忍不住想……','I was tempted to check my messages.'),('squeeze in','抽空做……','I managed to squeeze in some English practice.'),('refreshed afterward','之后精神焕发','I felt refreshed afterward.')],
27:[('get to','到达……','I am trying to get to the conference center.'),('get back on track','回到正确路线','I needed to get back on track.'),('in the opposite direction','朝相反方向','Take the train in the opposite direction.'),('just to make sure','确认一下','Just to make sure I understood correctly.'),('it should take about','预计需要约……','It should take about twenty minutes.')],
28:[('have some concerns about','对……有顾虑','I have some concerns about the testing schedule.'),('not necessarily','不一定','Not necessarily; we could test it first.'),('focused load test','有针对性的负载测试','Let’s run a focused load test.'),('move forward as planned','按计划推进','We can move forward as planned.'),('rather than','而不是……','Offer an option rather than delaying immediately.')],
29:[('strike a balance between','在……之间取得平衡','We need to strike a balance between performance and consistency.'),('rather than','与其……不如……','Rather than caching everything, start with common data.'),('outweigh the drawbacks','利大于弊','The benefits may outweigh the drawbacks.'),('understand the trade-offs','了解取舍','We need to understand the trade-offs.'),('meet business requirements','满足业务需求','The design should meet business requirements.')],
}
questions={
22:[('Why was scheduling the meeting difficult?','为什么安排会议时间有难度？','It was difficult because the team worked across different time zones.'),('What solution did the team choose?','团队采用了什么解决办法？','They decided to rotate meeting times and use written updates.'),('How would you ask a manager to change a meeting time?','你会如何要求经理调整开会时间？','Could we consider rotating meeting times so nobody always has to stay late?')],
23:[('Why was the flight delayed?','航班为什么延误？','The flight was delayed because of a technical issue.'),('How did the traveler adjust the plans?','旅行者如何调整计划？','He informed the hotel and colleagues about his late arrival.'),('How would you explain a project delay to a manager?','你如何向经理解释项目延期？','We have encountered an unexpected issue, and I will keep you informed.')],
24:[('What happened to the Java service?','Java 服务发生了什么？','The service became unusually slow.'),('What was the suspected cause?','排查发现什么原因？','The database connection pool was nearly exhausted.'),('How would you report an incident in English?','你会如何用英语汇报故障？','We are investigating the issue and will share a more accurate estimate soon.')],
25:[('What was the engineer working on?','工程师正在排查什么？','He was investigating a performance issue.'),('What was the next step?','下一步要做什么？','He planned to run a load test.'),('Give a three-sentence stand-up update.','试着用三句话做每日站会汇报。','I am working on a bug. I have narrowed down the cause. My next step is to run more tests.')],
26:[('Where did the family go?','一家人去了哪里？','They went to a nearby park.'),('How did the writer make time for English?','作者什么时候练习英语？','He practiced English while his son was taking a nap.'),('Describe your weekend in four English sentences.','用四句英语描述你的周末。','I went to a park. I spent time with my family. I practiced English. I felt refreshed afterward.')],
27:[('Why did the traveler get off the train?','旅行者为什么下车？','He took a train in the wrong direction.'),('How did he confirm the instructions?','他如何确认方向？','He repeated the route to the employee.'),('How would you confirm a technical requirement?','你会怎样确认一个技术需求？','Just to make sure I understood correctly, we should finish testing before deployment, right?')],
28:[('What concerned the engineer?','工程师有什么顾虑？','The team had not tested the feature under heavy traffic.'),('What alternative did he suggest?','他提出什么替代方案？','He suggested running a focused load test.'),('How would you disagree politely in a meeting?','你如何在会议上礼貌表达不同意见？','I understand the deadline, but I am concerned about skipping tests. Could we test first?')],
29:[('Why did the team consider Redis?','团队为什么考虑Redis？','They wanted to improve performance and reduce database load.'),('What trade-offs did they discuss?','团队讨论了哪些取舍？','They discussed performance, data consistency and complexity.'),('Explain a trade-off in a technology you know.','描述你熟悉的一项技术取舍。','Redis improves performance, but it adds consistency issues. We need to balance speed and complexity.')],
}
for l in sections:
    l['expressions']=[{'text':a,'meaning':b,'example':c} for a,b,c in exp[l['day']]]
    l['exercises']=[{'question':q,'questionZh':zh,'reference':ref,'type':'OPEN_SPEAKING' if i==2 else 'COMPREHENSION'} for i,(q,zh,ref) in enumerate(questions[l['day']])]
    assert all(s['en'] and s['zh'] for s in l['segments']),l['title']
    assert len(l['words'])>=8
out=path/'server/src/main/resources/seed/lessons.json'
out.write_text(json.dumps(sections,ensure_ascii=False,indent=2),encoding='utf-8')
(path/'web/public/lessons.json').write_text(json.dumps(sections,ensure_ascii=False,indent=2),encoding='utf-8')
print('Seed generated:',len(sections),'lessons',sum(len(l['segments']) for l in sections),'paragraphs',sum(len(l['words']) for l in sections),'vocab')
